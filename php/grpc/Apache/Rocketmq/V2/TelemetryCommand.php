<?php
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: apache/rocketmq/v2/service.proto

namespace Apache\Rocketmq\V2;

use Google\Protobuf\Internal\GPBType;
use Google\Protobuf\Internal\RepeatedField;
use Google\Protobuf\Internal\GPBUtil;

/**
 * Generated from protobuf message <code>apache.rocketmq.v2.TelemetryCommand</code>
 */
class TelemetryCommand extends \Google\Protobuf\Internal\Message
{
    /**
     * Generated from protobuf field <code>optional .apache.rocketmq.v2.Status status = 1;</code>
     */
    protected $status = null;
    protected $command;

    /**
     * Constructor.
     *
     * @param array $data {
     *     Optional. Data for populating the Message object.
     *
     *     @type \Apache\Rocketmq\V2\Status $status
     *     @type \Apache\Rocketmq\V2\Settings $settings
     *           Client settings
     *     @type \Apache\Rocketmq\V2\ThreadStackTrace $thread_stack_trace
     *           These messages are from client.
     *           Report thread stack trace to server.
     *     @type \Apache\Rocketmq\V2\VerifyMessageResult $verify_message_result
     *           Report message verify result to server.
     *     @type \Apache\Rocketmq\V2\RecoverOrphanedTransactionCommand $recover_orphaned_transaction_command
     *           There messages are from server.
     *           Request client to recover the orphaned transaction message.
     *     @type \Apache\Rocketmq\V2\PrintThreadStackTraceCommand $print_thread_stack_trace_command
     *           Request client to print thread stack trace.
     *     @type \Apache\Rocketmq\V2\VerifyMessageCommand $verify_message_command
     *           Request client to verify the consumption of the appointed message.
     * }
     */
    public function __construct($data = NULL) {
        \GPBMetadata\Apache\Rocketmq\V2\Service::initOnce();
        parent::__construct($data);
    }

    /**
     * Generated from protobuf field <code>optional .apache.rocketmq.v2.Status status = 1;</code>
     * @return \Apache\Rocketmq\V2\Status|null
     */
    public function getStatus()
    {
        return $this->status;
    }

    public function hasStatus()
    {
        return isset($this->status);
    }

    public function clearStatus()
    {
        unset($this->status);
    }

    /**
     * Generated from protobuf field <code>optional .apache.rocketmq.v2.Status status = 1;</code>
     * @param \Apache\Rocketmq\V2\Status $var
     * @return $this
     */
    public function setStatus($var)
    {
        GPBUtil::checkMessage($var, \Apache\Rocketmq\V2\Status::class);
        $this->status = $var;

        return $this;
    }

    /**
     * Client settings
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.Settings settings = 2;</code>
     * @return \Apache\Rocketmq\V2\Settings|null
     */
    public function getSettings()
    {
        return $this->readOneof(2);
    }

    public function hasSettings()
    {
        return $this->hasOneof(2);
    }

    /**
     * Client settings
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.Settings settings = 2;</code>
     * @param \Apache\Rocketmq\V2\Settings $var
     * @return $this
     */
    public function setSettings($var)
    {
        GPBUtil::checkMessage($var, \Apache\Rocketmq\V2\Settings::class);
        $this->writeOneof(2, $var);

        return $this;
    }

    /**
     * These messages are from client.
     * Report thread stack trace to server.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.ThreadStackTrace thread_stack_trace = 3;</code>
     * @return \Apache\Rocketmq\V2\ThreadStackTrace|null
     */
    public function getThreadStackTrace()
    {
        return $this->readOneof(3);
    }

    public function hasThreadStackTrace()
    {
        return $this->hasOneof(3);
    }

    /**
     * These messages are from client.
     * Report thread stack trace to server.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.ThreadStackTrace thread_stack_trace = 3;</code>
     * @param \Apache\Rocketmq\V2\ThreadStackTrace $var
     * @return $this
     */
    public function setThreadStackTrace($var)
    {
        GPBUtil::checkMessage($var, \Apache\Rocketmq\V2\ThreadStackTrace::class);
        $this->writeOneof(3, $var);

        return $this;
    }

    /**
     * Report message verify result to server.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.VerifyMessageResult verify_message_result = 4;</code>
     * @return \Apache\Rocketmq\V2\VerifyMessageResult|null
     */
    public function getVerifyMessageResult()
    {
        return $this->readOneof(4);
    }

    public function hasVerifyMessageResult()
    {
        return $this->hasOneof(4);
    }

    /**
     * Report message verify result to server.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.VerifyMessageResult verify_message_result = 4;</code>
     * @param \Apache\Rocketmq\V2\VerifyMessageResult $var
     * @return $this
     */
    public function setVerifyMessageResult($var)
    {
        GPBUtil::checkMessage($var, \Apache\Rocketmq\V2\VerifyMessageResult::class);
        $this->writeOneof(4, $var);

        return $this;
    }

    /**
     * There messages are from server.
     * Request client to recover the orphaned transaction message.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.RecoverOrphanedTransactionCommand recover_orphaned_transaction_command = 5;</code>
     * @return \Apache\Rocketmq\V2\RecoverOrphanedTransactionCommand|null
     */
    public function getRecoverOrphanedTransactionCommand()
    {
        return $this->readOneof(5);
    }

    public function hasRecoverOrphanedTransactionCommand()
    {
        return $this->hasOneof(5);
    }

    /**
     * There messages are from server.
     * Request client to recover the orphaned transaction message.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.RecoverOrphanedTransactionCommand recover_orphaned_transaction_command = 5;</code>
     * @param \Apache\Rocketmq\V2\RecoverOrphanedTransactionCommand $var
     * @return $this
     */
    public function setRecoverOrphanedTransactionCommand($var)
    {
        GPBUtil::checkMessage($var, \Apache\Rocketmq\V2\RecoverOrphanedTransactionCommand::class);
        $this->writeOneof(5, $var);

        return $this;
    }

    /**
     * Request client to print thread stack trace.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.PrintThreadStackTraceCommand print_thread_stack_trace_command = 6;</code>
     * @return \Apache\Rocketmq\V2\PrintThreadStackTraceCommand|null
     */
    public function getPrintThreadStackTraceCommand()
    {
        return $this->readOneof(6);
    }

    public function hasPrintThreadStackTraceCommand()
    {
        return $this->hasOneof(6);
    }

    /**
     * Request client to print thread stack trace.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.PrintThreadStackTraceCommand print_thread_stack_trace_command = 6;</code>
     * @param \Apache\Rocketmq\V2\PrintThreadStackTraceCommand $var
     * @return $this
     */
    public function setPrintThreadStackTraceCommand($var)
    {
        GPBUtil::checkMessage($var, \Apache\Rocketmq\V2\PrintThreadStackTraceCommand::class);
        $this->writeOneof(6, $var);

        return $this;
    }

    /**
     * Request client to verify the consumption of the appointed message.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.VerifyMessageCommand verify_message_command = 7;</code>
     * @return \Apache\Rocketmq\V2\VerifyMessageCommand|null
     */
    public function getVerifyMessageCommand()
    {
        return $this->readOneof(7);
    }

    public function hasVerifyMessageCommand()
    {
        return $this->hasOneof(7);
    }

    /**
     * Request client to verify the consumption of the appointed message.
     *
     * Generated from protobuf field <code>.apache.rocketmq.v2.VerifyMessageCommand verify_message_command = 7;</code>
     * @param \Apache\Rocketmq\V2\VerifyMessageCommand $var
     * @return $this
     */
    public function setVerifyMessageCommand($var)
    {
        GPBUtil::checkMessage($var, \Apache\Rocketmq\V2\VerifyMessageCommand::class);
        $this->writeOneof(7, $var);

        return $this;
    }

    /**
     * @return string
     */
    public function getCommand()
    {
        return $this->whichOneof("command");
    }

}
