package org.lee.rocket.train.payment.task;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.lee.rocket.train.api.IMqMessageProducerService;
import org.lee.rocket.train.service.entity.MqMessageProducer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付回调 MQ 消息发送补偿定时任务
 *
 * 补偿对象：tb_mq_message_producer 表（实体：MqMessageProducer）
 * 补偿场景：PaymentServiceImpl.callbackPayment() 支付回调中，
 *          MQ 消息发送失败（msg_status=2）的记录，由本定时任务定期重发补偿
 */
@Slf4j
@Component
public class PaymentMqSendCompensateTask {

    /** 补偿阈值：创建超过此时间的失败记录才纳入补偿（避免与正在发送的消息冲突） */
    private static final int COMPENSATE_THRESHOLD_MINUTES = 1;

    @Resource
    private IMqMessageProducerService mqMessageProducerService;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 每 60 秒执行一次补偿扫描
     * 扫描条件：msg_status = 2（发送失败）且创建时间超过 1 分钟
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void compensatePaymentSendFailures() {
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(COMPENSATE_THRESHOLD_MINUTES);

        // 查询支付回调中 MQ 发送失败的记录
        @SuppressWarnings("null")
        List<MqMessageProducer> failedPaymentMessages = mqMessageProducerService.lambdaQuery()
                .eq(MqMessageProducer::getMsgStatus, 2)
                .lt(MqMessageProducer::getCreateTime, thresholdTime)
                .list();

        if (failedPaymentMessages.isEmpty()) {
            return;
        }

        log.info("支付MQ补偿任务启动, 待重发消息数量: {}", failedPaymentMessages.size());

        for (MqMessageProducer msg : failedPaymentMessages) {
            resendPaymentMessage(msg);
        }
    }

    /**
     * 重发单条支付 MQ 消息
     */
    private void resendPaymentMessage(MqMessageProducer msg) {
        try {
            Message message = new Message(
                    msg.getMsgTopic(),
                    msg.getMsgTag(),
                    msg.getMsgKey(),
                    msg.getMsgBody().getBytes()
            );

            SendResult sendResult = rocketMQTemplate.getProducer().send(message);

            if (sendResult.getSendStatus().equals(SendStatus.SEND_OK)) {
                // 重发成功 → 删除记录
                mqMessageProducerService.removeById(msg.getId());
                log.info("支付MQ补偿重发成功, 删除记录: id={}, topic={}, key={}",
                        msg.getId(), msg.getMsgTopic(), msg.getMsgKey());
            } else {
                // 仍然失败 → 保持状态，等待下次补偿
                msg.setMsgStatus(2);
                mqMessageProducerService.updateById(msg);
                log.warn("支付MQ补偿重发仍然失败: id={}, status={}", msg.getId(), sendResult.getSendStatus());
            }
        } catch (Exception e) {
            // 异常 → 保持状态，等待下次补偿
            msg.setMsgStatus(2);
            mqMessageProducerService.updateById(msg);
            log.error("支付MQ补偿重发异常: id={}, error={}", msg.getId(), e.getMessage());
        }
    }
}
