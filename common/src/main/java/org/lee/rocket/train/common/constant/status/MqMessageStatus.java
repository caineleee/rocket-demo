package org.lee.rocket.train.common.constant.status;

import lombok.Getter;

/**
 * MQ 消息状态（写入 tb_mq_message_producer.msg_status 和 tb_mq_consumer_log.status 列）
 * <p>
 * 状态值：
 * <pre>
 *   PROCESSING(0) 处理中 —— 消息已持久化，尚未发送/消费完成
 *   SUCCESS(1)   成功   —— 发送成功后从 DB 删除，或消费成功
 *   FAIL(2)       失败   —— 发送/消费失败，由补偿任务重试
 * </pre>
 * <p>
 * 设计说明：
 * - 此状态由补偿任务驱动（发送成功就删记录，失败就标 FAIL 等重试），不是业务状态机
 * - 故不提供 canTransitionTo，流转由补偿逻辑控制
 *
 * @author lihongliang
 */
@Getter
public enum MqMessageStatus {

    PROCESSING(0, "处理中"),
    SUCCESS(1, "成功"),
    FAIL(2, "失败");

    private final int code;
    private final String desc;

    MqMessageStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MqMessageStatus of(int code) {
        for (MqMessageStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知MQ消息状态码: " + code);
    }
}
