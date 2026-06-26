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
 * MQ消息生产表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("tb_mq_message_producer")
public class MqMessageProducer implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（通常为消息唯一ID，如 UUID 或 Snowflake ID）
     */
    @TableId("id")
    private String id;

    /**
     * 生产者组名
     */
    @TableField("group_name")
    private String groupName;

    /**
     * 消息主题
     */
    @TableField("msg_topic")
    private String msgTopic;

    /**
     * Tag
     */
    @TableField("msg_tag")
    private String msgTag;

    /**
     * Key
     */
    @TableField("msg_key")
    private String msgKey;

    /**
     * 消息内容（建议不超过 5KB，超长可存 OSS 并记录 URL）
     */
    @TableField("msg_body")
    private String msgBody;

    /**
     * 消息状态：0未处理 1已处理
     */
    @TableField("msg_status")
    private Boolean msgStatus;

    /**
     * 记录时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
