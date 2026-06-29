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
 * mq 消息消费日志表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-29
 */
@Getter
@Setter
@ToString
@TableName("tb_mq_consumer_log")
public class MqConsumerLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * messageId
     */
    @TableField("msg_id")
    private String msgId;

    @TableField("group_name")
    private String groupName;

    @TableField("msg_tag")
    private String msgTag;

    @TableField("msg_key")
    private String msgKey;

    @TableField("msg_body")
    private String msgBody;

    /**
     * 0:处理中, 1:处理成功, 2:处理失败
     */
    @TableField("consumer_status")
    private Boolean consumerStatus;

    /**
     * 消费重试次数,最多不超过3次
     */
    @TableField("consumer_times")
    private Boolean consumerTimes;

    /**
     * 消费时间
     */
    @TableField("consumer_time")
    private LocalDateTime consumerTime;

    @TableField("remark")
    private String remark;
}
