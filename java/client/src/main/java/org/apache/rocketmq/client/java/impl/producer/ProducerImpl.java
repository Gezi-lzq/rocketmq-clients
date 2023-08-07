/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.client.java.impl.producer;

import static com.google.common.base.Preconditions.checkNotNull;

import apache.rocketmq.v2.ClientType;
import apache.rocketmq.v2.Code;
import apache.rocketmq.v2.EndTransactionRequest;
import apache.rocketmq.v2.EndTransactionResponse;
import apache.rocketmq.v2.HeartbeatRequest;
import apache.rocketmq.v2.NotifyClientTerminationRequest;
import apache.rocketmq.v2.RecoverOrphanedTransactionCommand;
import apache.rocketmq.v2.SendMessageRequest;
import apache.rocketmq.v2.SendMessageResponse;
import apache.rocketmq.v2.Status;
import com.google.common.math.IntMath;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.javacrumbs.futureconverter.java8guava.FutureConverter;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.apis.producer.TransactionChecker;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.apache.rocketmq.client.java.exception.InternalErrorException;
import org.apache.rocketmq.client.java.exception.TooManyRequestsException;
import org.apache.rocketmq.client.java.hook.MessageHookPoints;
import org.apache.rocketmq.client.java.hook.MessageHookPointsStatus;
import org.apache.rocketmq.client.java.hook.MessageInterceptorContextImpl;
import org.apache.rocketmq.client.java.impl.ClientImpl;
import org.apache.rocketmq.client.java.impl.Settings;
import org.apache.rocketmq.client.java.message.GeneralMessage;
import org.apache.rocketmq.client.java.message.GeneralMessageImpl;
import org.apache.rocketmq.client.java.message.MessageType;
import org.apache.rocketmq.client.java.message.MessageViewImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;
import org.apache.rocketmq.client.java.retry.ExponentialBackoffRetryPolicy;
import org.apache.rocketmq.client.java.retry.RetryPolicy;
import org.apache.rocketmq.client.java.route.Endpoints;
import org.apache.rocketmq.client.java.route.MessageQueueImpl;
import org.apache.rocketmq.client.java.route.TopicRouteData;
import org.apache.rocketmq.client.java.rpc.RpcFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link Producer}
 *
 * @see Producer
 */
@SuppressWarnings({"UnstableApiUsage", "NullableProblems"})
class ProducerImpl extends ClientImpl implements Producer {
    private static final Logger log = LoggerFactory.getLogger(ProducerImpl.class);

    protected final PublishingSettings publishingSettings;
    final ConcurrentMap<String/* topic */, PublishingLoadBalancer> publishingRouteDataCache;
    private final TransactionChecker checker;

    /**
     * The caller is supposed to have validated the arguments and handled throwing exception or
     * logging warnings already, so we avoid repeating args check here.
     */
    ProducerImpl(ClientConfiguration clientConfiguration, Set<String> topics, int maxAttempts,
        TransactionChecker checker) {
        super(clientConfiguration, topics);
        ExponentialBackoffRetryPolicy retryPolicy = ExponentialBackoffRetryPolicy.immediatelyRetryPolicy(maxAttempts);
        this.publishingSettings = new PublishingSettings(clientId, endpoints, retryPolicy,
            clientConfiguration.getRequestTimeout(), topics);
        this.checker = checker;
        this.publishingRouteDataCache = new ConcurrentHashMap<>();
    }

    @Override
    protected void startUp() throws Exception {
        try {
            log.info("Begin to start the rocketmq producer, clientId={}", clientId);
            super.startUp();
            log.info("The rocketmq producer starts successfully, clientId={}", clientId);
        } catch (Throwable t) {
            log.error("Failed to start the rocketmq producer, try to shutdown it, clientId={}", clientId, t);
            shutDown();
            throw t;
        }
    }

    @Override
    protected void shutDown() throws InterruptedException {
        log.info("Begin to shutdown the rocketmq producer, clientId={}", clientId);
        super.shutDown();
        log.info("Shutdown the rocketmq producer successfully, clientId={}", clientId);
    }

    @Override
    public void onRecoverOrphanedTransactionCommand(Endpoints endpoints, RecoverOrphanedTransactionCommand command) {
        final String transactionId = command.getTransactionId();
        final String messageId = command.getMessage().getSystemProperties().getMessageId();
        if (null == checker) {
            log.error("No transaction checker registered, ignore it, messageId={}, transactionId={}, endpoints={},"
                + " clientId={}", messageId, transactionId, endpoints, clientId);
            return;
        }
        MessageViewImpl messageView;
        try {
            messageView = MessageViewImpl.fromProtobuf(command.getMessage());
        } catch (Throwable t) {
            log.error("[Bug] Failed to decode message during orphaned transaction message recovery, messageId={}, "
                + "transactionId={}, endpoints={}, clientId={}", messageId, transactionId, endpoints, clientId, t);
            return;
        }
        ListenableFuture<TransactionResolution> future;
        try {
            final ListeningExecutorService service = MoreExecutors.listeningDecorator(telemetryCommandExecutor);
            final Callable<TransactionResolution> task = () -> checker.check(messageView);
            future = service.submit(task);
        } catch (Throwable t) {
            final SettableFuture<TransactionResolution> future0 = SettableFuture.create();
            future0.setException(t);
            future = future0;
        }
        Futures.addCallback(future, new FutureCallback<TransactionResolution>() {
            @Override
            public void onSuccess(TransactionResolution resolution) {
                try {
                    if (null == resolution || TransactionResolution.UNKNOWN.equals(resolution)) {
                        return;
                    }
                    final GeneralMessage generalMessage = new GeneralMessageImpl(messageView);
                    endTransaction(endpoints, generalMessage, messageView.getMessageId(),
                        transactionId, resolution);
                } catch (Throwable t) {
                    log.error("Exception raised while ending the transaction, messageId={}, transactionId={}, "
                        + "endpoints={}, clientId={}", messageId, transactionId, endpoints, clientId, t);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Exception raised while checking the transaction, messageId={}, transactionId={}, "
                    + "endpoints={}, clientId={}", messageId, transactionId, endpoints, clientId, t);

            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public Settings getSettings() {
        return publishingSettings;
    }

    @Override
    public NotifyClientTerminationRequest wrapNotifyClientTerminationRequest() {
        return NotifyClientTerminationRequest.newBuilder().build();
    }

    @Override
    public HeartbeatRequest wrapHeartbeatRequest() {
        return HeartbeatRequest.newBuilder().setClientType(ClientType.PRODUCER).build();
    }

    /**
     * @see Producer#send(Message)
     */
    @Override
    public SendReceipt send(Message message) throws ClientException {
        final ListenableFuture<SendReceipt> future = Futures.transform(send(Collections.singletonList(message), false),
            sendReceipts -> sendReceipts.iterator().next(), MoreExecutors.directExecutor());
        return handleClientFuture(future);
    }

    /**
     * @see Producer#send(Message, Transaction)
     */
    @Override
    public SendReceipt send(Message message, Transaction transaction) throws ClientException {
        if (!(transaction instanceof TransactionImpl)) {
            throw new IllegalArgumentException("Failed downcasting for transaction");
        }
        TransactionImpl transactionImpl = (TransactionImpl) transaction;
        final PublishingMessageImpl publishingMessage;
        try {
            publishingMessage = transactionImpl.tryAddMessage(message);
        } catch (Throwable t) {
            throw new ClientException(t);
        }
        final ListenableFuture<List<SendReceiptImpl>> future = send(Collections.singletonList(publishingMessage), true);
        final List<SendReceiptImpl> receipts = handleClientFuture(future);
        final SendReceiptImpl sendReceipt = receipts.iterator().next();
        ((TransactionImpl) transaction).tryAddReceipt(publishingMessage, sendReceipt);
        return sendReceipt;
    }

    /**
     * @see Producer#sendAsync(Message)
     */
    @Override
    public CompletableFuture<SendReceipt> sendAsync(Message message) {
        final ListenableFuture<SendReceipt> future = Futures.transform(send(Collections.singletonList(message), false),
            sendReceipts -> sendReceipts.iterator().next(), MoreExecutors.directExecutor());
        return FutureConverter.toCompletableFuture(future);
    }

    /**
     * @see Producer#beginTransaction()
     */
    @Override
    public Transaction beginTransaction() {
        checkNotNull(checker, "Transaction checker should not be null");
        if (!this.isRunning()) {
            log.error("Unable to begin a transaction because producer is not running, state={}, clientId={}",
                this.state(), clientId);
            throw new IllegalStateException("Producer is not running now");
        }
        return new TransactionImpl(this);
    }

    @Override
    public void close() {
        this.stopAsync().awaitTerminated();
    }

    public void endTransaction(Endpoints endpoints, GeneralMessage generalMessage, MessageId messageId,
        String transactionId, final TransactionResolution resolution) throws ClientException {
        final EndTransactionRequest.Builder builder = EndTransactionRequest.newBuilder()
            .setMessageId(messageId.toString()).setTransactionId(transactionId)
            .setTopic(apache.rocketmq.v2.Resource.newBuilder().setName(generalMessage.getTopic()).build());
        switch (resolution) {
            case COMMIT:
                builder.setResolution(apache.rocketmq.v2.TransactionResolution.COMMIT);
                break;
            case ROLLBACK:
            default:
                builder.setResolution(apache.rocketmq.v2.TransactionResolution.ROLLBACK);
        }
        final Duration requestTimeout = clientConfiguration.getRequestTimeout();
        final EndTransactionRequest request = builder.build();
        final List<GeneralMessage> generalMessages = Collections.singletonList(generalMessage);
        MessageHookPoints messageHookPoints = TransactionResolution.COMMIT.equals(resolution) ?
            MessageHookPoints.COMMIT_TRANSACTION : MessageHookPoints.ROLLBACK_TRANSACTION;
        final MessageInterceptorContextImpl context = new MessageInterceptorContextImpl(messageHookPoints);
        doBefore(context, generalMessages);

        final RpcFuture<EndTransactionRequest, EndTransactionResponse> future =
            this.getClientManager().endTransaction(endpoints, request, requestTimeout);
        Futures.addCallback(future, new FutureCallback<EndTransactionResponse>() {
            @Override
            public void onSuccess(EndTransactionResponse response) {
                final Status status = response.getStatus();
                final Code code = status.getCode();
                MessageHookPointsStatus hookPointsStatus = Code.OK.equals(code) ? MessageHookPointsStatus.OK :
                    MessageHookPointsStatus.ERROR;
                final MessageInterceptorContextImpl context0 = new MessageInterceptorContextImpl(context,
                    hookPointsStatus);
                doAfter(context0, generalMessages);
            }

            @Override
            public void onFailure(Throwable t) {
                final MessageInterceptorContextImpl context0 = new MessageInterceptorContextImpl(context,
                    MessageHookPointsStatus.ERROR);
                doAfter(context0, generalMessages);
            }
        }, MoreExecutors.directExecutor());
        final EndTransactionResponse response = handleClientFuture(future);
        final Status status = response.getStatus();
        final Code code = status.getCode();
        if (!Code.OK.equals(code)) {
            throw new ClientException(code.getNumber(), future.getContext().getRequestId(), status.getMessage());
        }
    }

    /**
     * Isolate specified {@link Endpoints}.
     */
    private void isolate(Endpoints endpoints) {
        isolated.add(endpoints);
    }

    private RetryPolicy getRetryPolicy() {
        return publishingSettings.getRetryPolicy();
    }

    /**
     * Take message queue(s) from route for message publishing.
     */
    private List<MessageQueueImpl> takeMessageQueues(PublishingLoadBalancer result) {
        return result.takeMessageQueues(isolated, this.getRetryPolicy().getMaxAttempts());
    }

    private ListenableFuture<List<SendReceiptImpl>> send(List<Message> messages, boolean txEnabled) {
        SettableFuture<List<SendReceiptImpl>> future = SettableFuture.create();

        // Check producer state before message publishing.
        if (!this.isRunning()) {
            final IllegalStateException e = new IllegalStateException("Producer is not running now");
            future.setException(e);
            log.error("Unable to send message because producer is not running, state={}, clientId={}",
                this.state(), clientId);
            return future;
        }
        // 创建一个空的ArrayList<PublishingMessageImpl>对象pubMessages，用于存储经过处理的消息
        List<PublishingMessageImpl> pubMessages = new ArrayList<>();
        for (Message message : messages) {
            try {
                // 遍历messages中的每个消息，将其转换为PublishingMessageImpl对象，并添加到pubMessages中
                final PublishingMessageImpl pubMessage = new PublishingMessageImpl(message, publishingSettings,
                    txEnabled);
                pubMessages.add(pubMessage);
            } catch (Throwable t) {
                // Failed to refine message, no need to proceed.
                log.error("Failed to refine message to send, clientId={}, message={}", clientId, message, t);
                future.setException(t);
                return future;
            }
        }

        // Collect topics to send message.
        final Set<String> topics = pubMessages.stream().map(Message::getTopic).collect(Collectors.toSet());
        if (1 < topics.size()) {
            // Messages have different topics, no need to proceed.
            final IllegalArgumentException e = new IllegalArgumentException("Messages to send have different topics");
            future.setException(e);
            log.error("Messages to be sent have different topics, no need to proceed, topic(s)={}, clientId={}",
                topics, clientId);
            return future;
        }
        // 获取唯一的主题topic
        final String topic = topics.iterator().next();
        // Collect message types.
        final Set<MessageType> messageTypes = pubMessages.stream()
            .map(PublishingMessageImpl::getMessageType)
            .collect(Collectors.toSet());
        if (1 < messageTypes.size()) {
            // Messages have different message type, no need to proceed.
            final IllegalArgumentException e = new IllegalArgumentException("Messages to send have different types, "
                + "please check");
            future.setException(e);
            log.error("Messages to be sent have different message types, no need to proceed, topic={}, messageType"
                + "(s)={}, clientId={}", topic, messageTypes, clientId, e);
            return future;
        }

        final MessageType messageType = messageTypes.iterator().next();
        final String messageGroup;

        // Message group must be same if message type is FIFO, or no need to proceed.
        if (MessageType.FIFO.equals(messageType)) {
            // 对于FIFO消息类型，收集所有消息的消息组，并存储在一个Set<String>对象messageGroups中
            final Set<String> messageGroups = pubMessages.stream()
                .map(PublishingMessageImpl::getMessageGroup).filter(Optional::isPresent)
                .map(Optional::get).collect(Collectors.toSet());

            if (1 < messageGroups.size()) {
                final IllegalArgumentException e = new IllegalArgumentException("FIFO messages to send have different "
                    + "message groups, messageGroups=" + messageGroups);
                future.setException(e);
                log.error("FIFO messages to be sent have different message groups, no need to proceed, topic={}, "
                    + "messageGroups={}, clientId={}", topic, messageGroups, clientId, e);
                return future;
            }
            messageGroup = messageGroups.iterator().next();
        } else {
            messageGroup = null;
        }
        // 将topic添加到topics集合中，表示此主题正在发送消息
        this.topics.add(topic);
        // Get publishing topic route.
        final ListenableFuture<PublishingLoadBalancer> routeFuture = getPublishingLoadBalancer(topic);
        return Futures.transformAsync(routeFuture, result -> {
            // Prepare the candidate message queue(s) for retry-sending in advance.
            final List<MessageQueueImpl> candidates = null == messageGroup ? takeMessageQueues(result) :
                Collections.singletonList(result.takeMessageQueueByMessageGroup(messageGroup));
            final SettableFuture<List<SendReceiptImpl>> future0 = SettableFuture.create();
            send0(future0, topic, messageType, candidates, pubMessages, 1);
            return future0;
        }, MoreExecutors.directExecutor());
    }

    /**
     * The caller is supposed to make sure different messages have the same message type and same topic.
     */
    private SendMessageRequest wrapSendMessageRequest(List<PublishingMessageImpl> pubMessages, MessageQueueImpl mq) {
        final List<apache.rocketmq.v2.Message> messages = pubMessages.stream()
            .map(publishingMessage -> publishingMessage.toProtobuf(mq)).collect(Collectors.toList());
        return SendMessageRequest.newBuilder().addAllMessages(messages).build();
    }

    ListenableFuture<List<SendReceiptImpl>> send0(Endpoints endpoints, List<PublishingMessageImpl> pubMessages,
        MessageQueueImpl mq) {
        final SendMessageRequest request = wrapSendMessageRequest(pubMessages, mq);
        final RpcFuture<SendMessageRequest, SendMessageResponse> future0 =
            this.getClientManager().sendMessage(endpoints, request, clientConfiguration.getRequestTimeout());
        return Futures.transformAsync(future0,
            response -> Futures.immediateFuture(SendReceiptImpl.processResponseInvocation(mq, response, future0)),
            MoreExecutors.directExecutor());
    }

    /**
     * Warning: please DO NOT modify the signature of this method, it is used by OpenTelemetry instrumentation.
     */
    private void send0(SettableFuture<List<SendReceiptImpl>> future0, String topic, MessageType messageType,
        final List<MessageQueueImpl> candidates, final List<PublishingMessageImpl> messages, final int attempt) {
        // Calculate the current message queue.
        final MessageQueueImpl mq = candidates.get(IntMath.mod(attempt - 1, candidates.size()));
        final List<MessageType> acceptMessageTypes = mq.getAcceptMessageTypes();
        if (publishingSettings.isValidateMessageType() && !acceptMessageTypes.contains(messageType)) {
            final IllegalArgumentException e = new IllegalArgumentException("Current message type not match with "
                + "topic accept message types, topic=" + topic + ", actualMessageType=" + messageType + ", "
                + "acceptMessageTypes=" + acceptMessageTypes);
            future0.setException(e);
            return;
        }
        final Endpoints endpoints = mq.getBroker().getEndpoints();
        final ListenableFuture<List<SendReceiptImpl>> future = send0(endpoints, messages, mq);
        final int maxAttempts = this.getRetryPolicy().getMaxAttempts();

        // Intercept before message publishing.
        final List<GeneralMessage> generalMessages = messages.stream().map((Function<PublishingMessageImpl,
            GeneralMessage>) GeneralMessageImpl::new).collect(Collectors.toList());
        final MessageInterceptorContextImpl context = new MessageInterceptorContextImpl(MessageHookPoints.SEND);
        doBefore(context, generalMessages);

        Futures.addCallback(future, new FutureCallback<List<SendReceiptImpl>>() {
            @Override
            public void onSuccess(List<SendReceiptImpl> sendReceipts) {
                // Should never reach here.
                if (sendReceipts.size() != messages.size()) {
                    final InternalErrorException e = new InternalErrorException("[Bug] due to an"
                        + " unknown reason from remote, received send receipt's quantity " + sendReceipts.size()
                        + " is not equal to sent message's quantity " + messages.size());
                    future0.setException(e);

                    // Intercept after message publishing.
                    final MessageInterceptorContextImpl context0 = new MessageInterceptorContextImpl(context,
                        MessageHookPointsStatus.ERROR);
                    doAfter(context0, generalMessages);

                    return;
                }
                // Intercept after message publishing.
                final MessageInterceptorContextImpl context0 = new MessageInterceptorContextImpl(context,
                    MessageHookPointsStatus.OK);
                doAfter(context0, generalMessages);

                // No need more attempts.
                future0.set(sendReceipts);
                // Resend message(s) successfully.
                if (1 < attempt) {
                    // Collect messageId(s) for logging.
                    List<MessageId> messageIds = new ArrayList<>();
                    for (SendReceipt receipt : sendReceipts) {
                        messageIds.add(receipt.getMessageId());
                    }
                    log.info("Resend message successfully, topic={}, messageId(s)={}, maxAttempts={}, "
                            + "attempt={}, endpoints={}, clientId={}", topic, messageIds, maxAttempts, attempt,
                        endpoints, clientId);
                }
                // Send message(s) successfully on first attempt, return directly.
            }

            @Override
            public void onFailure(Throwable t) {
                // Intercept after message publishing.
                final MessageInterceptorContextImpl context0 = new MessageInterceptorContextImpl(context,
                    MessageHookPointsStatus.ERROR);
                doAfter(context0, generalMessages);

                // Collect messageId(s) for logging.
                List<MessageId> messageIds = new ArrayList<>();
                // 循环遍历messages列表，收集每个消息的messageId，并存储在messageIds列表中，用于日志记录
                for (PublishingMessageImpl message : messages) {
                    messageIds.add(message.getMessageId());
                }
                // Isolate endpoints because of sending failure.
                // Isolate the current endpoint for publishing.
                isolate(endpoints);
                // Return failure and end the current process if the attempt times is run out,
                // otherwirse deecide to retry or not according to the error type.
                if (attempt >= maxAttempts) {
                    // No need more attempts.
                    future0.setException(t);
                    log.error("Failed to send message(s) finally, run out of attempt times, maxAttempts={}, " +
                            "attempt={}, topic={}, messageId(s)={}, endpoints={}, clientId={}",
                        maxAttempts, attempt, topic, messageIds, endpoints, clientId, t);
                    // Return failure and end the current process if there is no need to retry, otherwise go to the next step.
                    return;
                }
                // No need more attempts for transactional message.
                // 对于事务消息（MessageType.TRANSACTION），不再进行更多的attempts
                if (MessageType.TRANSACTION.equals(messageType)) {
                    future0.setException(t);
                    log.error("Failed to send transactional message finally, maxAttempts=1, attempt={}, " +
                            "topic={}, messageId(s)={}, endpoints={}, clientId={}", attempt, topic, messageIds,
                        endpoints, clientId, t);
                    // Return failure and end the current process if there is no need to retry, otherwise go to the next step.
                    return;
                }
                // Try to do more attempts.
                int nextAttempt = 1 + attempt;
                // Retry immediately if the request is not throttled.
                // 如果请求未被限流（t不是TooManyRequestsException类型），则立即重试发送消息
                if (!(t instanceof TooManyRequestsException)) {
                    log.warn("Failed to send message, would attempt to resend right now, maxAttempts={}, "
                            + "attempt={}, topic={}, messageId(s)={}, endpoints={}, clientId={}", maxAttempts, attempt,
                        topic, messageIds, endpoints, clientId, t);
                    // Rotate to next message queue to publish message, and go to send step
                    send0(future0, topic, messageType, candidates, messages, nextAttempt);
                    return;
                }
                // 如果请求被限流（t是TooManyRequestsException类型）
                // 根据重试策略获取下一次重试的延迟时间delay。
                final Duration delay = ProducerImpl.this.getRetryPolicy().getNextAttemptDelay(nextAttempt);
                // 记录警告日志指出由于请求过多而发送消息失败，并在延迟时间后调度重新发送消息的操作。
                log.warn("Failed to send message due to too many requests, would attempt to resend after {}, "
                        + "maxAttempts={}, attempt={}, topic={}, messageId(s)={}, endpoints={}, clientId={}", delay,
                    maxAttempts, attempt, topic, messageIds, endpoints, clientId, t);
                ProducerImpl.this.getClientManager().getScheduler().schedule(() -> send0(future0, topic, messageType,
                    candidates, messages, nextAttempt), delay.toNanos(), TimeUnit.NANOSECONDS);
            }
        }, clientCallbackExecutor);
    }

    private PublishingLoadBalancer updatePublishingLoadBalancer(String topic, TopicRouteData topicRouteData) {
        PublishingLoadBalancer publishingLoadBalancer = publishingRouteDataCache.get(topic);
        publishingLoadBalancer = null == publishingLoadBalancer ? new PublishingLoadBalancer(topicRouteData) :
            publishingLoadBalancer.update(topicRouteData);
        publishingRouteDataCache.put(topic, publishingLoadBalancer);
        return publishingLoadBalancer;
    }

    @Override
    public void onTopicRouteDataUpdate0(String topic, TopicRouteData topicRouteData) {
        updatePublishingLoadBalancer(topic, topicRouteData);
    }

    private ListenableFuture<PublishingLoadBalancer> getPublishingLoadBalancer(final String topic) {
        // 尝试从publishingRouteDataCache中获取已缓存的与topic相关的PublishingLoadBalancer对象
        final PublishingLoadBalancer loadBalancer = publishingRouteDataCache.get(topic);
        if (null != loadBalancer) {
            // 表示缓存中存在对应的LoadBalancer，
            // 那么直接使用Futures.immediateFuture(loadBalancer)将其包装成已完成的ListenableFuture对象，并立即返回
            return Futures.immediateFuture(loadBalancer);
        }
        // 对上一步的异步操作结果进行转换，当异步操作成功完成后，会执行给定的转换函数来更新发布LoadBalancer
        return Futures.transform(
                // 调用getRouteData(topic)方法获取与topic相关的路由数据（TopicRouteData）的异步操作的ListenableFuture对象
                getRouteData(topic),
                // 使用lambda表达式，接受topicRouteData作为参数
                // 调用updatePublishingLoadBalancer()方法进行LoadBalancer的更新
                topicRouteData -> updatePublishingLoadBalancer(topic, topicRouteData),
                // 作为执行转换函数的执行器
                MoreExecutors.directExecutor());
    }
}
