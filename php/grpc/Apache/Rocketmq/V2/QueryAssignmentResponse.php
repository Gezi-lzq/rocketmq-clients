<?php
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: apache/rocketmq/v2/service.proto

namespace Apache\Rocketmq\V2;

use Google\Protobuf\Internal\GPBType;
use Google\Protobuf\Internal\RepeatedField;
use Google\Protobuf\Internal\GPBUtil;

/**
 * Generated from protobuf message <code>apache.rocketmq.v2.QueryAssignmentResponse</code>
 */
class QueryAssignmentResponse extends \Google\Protobuf\Internal\Message
{
    /**
     * Generated from protobuf field <code>.apache.rocketmq.v2.Status status = 1;</code>
     */
    protected $status = null;
    /**
     * Generated from protobuf field <code>repeated .apache.rocketmq.v2.Assignment assignments = 2;</code>
     */
    private $assignments;

    /**
     * Constructor.
     *
     * @param array $data {
     *     Optional. Data for populating the Message object.
     *
     *     @type \Apache\Rocketmq\V2\Status $status
     *     @type array<\Apache\Rocketmq\V2\Assignment>|\Google\Protobuf\Internal\RepeatedField $assignments
     * }
     */
    public function __construct($data = NULL) {
        \GPBMetadata\Apache\Rocketmq\V2\Service::initOnce();
        parent::__construct($data);
    }

    /**
     * Generated from protobuf field <code>.apache.rocketmq.v2.Status status = 1;</code>
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
     * Generated from protobuf field <code>.apache.rocketmq.v2.Status status = 1;</code>
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
     * Generated from protobuf field <code>repeated .apache.rocketmq.v2.Assignment assignments = 2;</code>
     * @return \Google\Protobuf\Internal\RepeatedField
     */
    public function getAssignments()
    {
        return $this->assignments;
    }

    /**
     * Generated from protobuf field <code>repeated .apache.rocketmq.v2.Assignment assignments = 2;</code>
     * @param array<\Apache\Rocketmq\V2\Assignment>|\Google\Protobuf\Internal\RepeatedField $var
     * @return $this
     */
    public function setAssignments($var)
    {
        $arr = GPBUtil::checkRepeatedField($var, \Google\Protobuf\Internal\GPBType::MESSAGE, \Apache\Rocketmq\V2\Assignment::class);
        $this->assignments = $arr;

        return $this;
    }

}
