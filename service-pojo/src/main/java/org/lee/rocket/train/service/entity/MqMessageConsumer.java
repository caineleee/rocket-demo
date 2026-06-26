package org.lee.rocket.train.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * MQ消息消费表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("tb_mq_message_consumer")
public class MqMessageConsumer implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（关联生产表，必须非空）
     */
    @TableId("msg_id")
    private String msgId;

    /**
     * 消费者组名
     */
    @TableId("group_name")
    private String groupName;

    /**
     * Tag
     */
    @TableField("msg_tag")
    private String msgTag;

    /**
     * Key
     */
    @TableId("msg_key")
    private String msgKey;

    /**
     * 消息体（建议 ≤5KB；超长建议存外部存储）
     */
    @TableField("msg_body")
    private String msgBody;

    /**
     * 消费状态：0正在处理 1处理成功 2处理失败
     */
    @TableField("consumer_status")
    private Boolean consumerStatus;

    /**
     * 消费次数（防无限重试）
     */
    @TableField("consumer_times")
    private Byte consumerTimes;

    /**
     * 消费时间
     */
    @TableField("consumer_timestamp")
    private LocalDateTime consumerTimestamp;

    /**
     * 备注（如失败原因）
     */
    @TableField("remark")
    private String remark;
}
