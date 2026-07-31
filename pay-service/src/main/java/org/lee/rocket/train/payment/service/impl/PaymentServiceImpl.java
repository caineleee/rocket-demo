package org.lee.rocket.train.payment.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.lee.rocket.train.api.IMqMessageProducerService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.payment.mapper.OrderPaymentMapper;
import org.lee.rocket.train.service.entity.MqMessageProducer;
import org.lee.rocket.train.service.entity.Payment;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lee.rocket.train.api.IPaymentService;
import org.springframework.beans.factory.annotation.Value;


import java.time.LocalDateTime;

/**
 * <p>
 * 订单支付表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Slf4j
@DubboService(interfaceClass = IPaymentService.class)
public class PaymentServiceImpl extends ServiceImpl<OrderPaymentMapper, Payment> implements IPaymentService {

    @Value("${rocketmq.producer.group}")
    private String groupName;

    @Value("${rocketmq.topics.pay}")
    private String topic;

    @Value("${rocketmq.tags.pay}")
    private String tag;

    @Resource
    private DefaultIdentifierGenerator idWorker;

    @Resource
    private IMqMessageProducerService mqMessageProducerService;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 创建支付订单
     *
     * @param payment 支付订单
     */
    @Override
    public Result<?> createPayment(Payment payment) {
        if (payment == null || payment.getOrderId() == null) {
            CastException.cast(ShopCode.REQUEST_PARAMETER_VALID);
        }
        // 判断订单支付状态
        @SuppressWarnings("null")
        Long count = lambdaQuery()
                .eq(Payment::getOrderId, payment.getOrderId())
                .eq(Payment::getIsPaid, ShopCode.PAYMENT_IS_PAID.getCode())
                .count();
        // 如果记录已经存在, 则抛出异常
        if (count > 0) {
            CastException.cast(ShopCode.PAYMENT_IS_PAID);
        }
        // 设置订单状态为: 未支付
        payment.setIsPaid(ShopCode.ORDER_PAY_STATUS_NO_PAY.getCode());
        // 保存支付订单
        payment.setPayId(idWorker.nextId(null));
        save(payment);

        return Result.success(ShopCode.SUCCESS);
    }

    /**
     * 支付回调
     *
     * @param payment 支付订单
     */
    @SuppressWarnings("null")
    @Override
    public Result<?> callbackPayment(Payment payment) {
        // 判断用户支付状态
        if (!payment.getIsPaid().equals(ShopCode.ORDER_PAY_STATUS_IS_PAY.getCode())) {
            CastException.cast(ShopCode.ORDER_PAY_STATUS_NO_PAY);
        }
        // 更新支付订单状态: 已支付
        Payment pay = lambdaQuery().eq(Payment::getPayId, payment.getPayId()).one();
        // 判断支付订单是否存在
        if (pay == null) {
            CastException.cast(ShopCode.PAYMENT_NOT_FOUND);
        }
        // 注意：这里写的是 DB 状态码（is_paid 列是 tinyint），必须用 ORDER_PAY_STATUS_IS_PAY(2)。
        // 之前错用 PAYMENT_IS_PAID(70002)，那是 API 响应码（供 CastException 抛业务异常用），
        // 70002 超出 tinyint 范围，导致 MySQL 报 "Data truncation: Out of range value for column 'is_paid'"。
        // 状态码与响应码不要混用：true 开头的 ShopCode 才是写进 DB 的状态值。
        pay.setIsPaid(ShopCode.ORDER_PAY_STATUS_IS_PAY.getCode());
        boolean updateResult = updateById(pay);
        if (!updateResult) {
            log.info("订单支付状态修改(->已支付)失败, 更新支付订单状态, payId: {}, 业务ID: {}",
                    payment.getPayId(), payment.getOrderId());
            CastException.cast(ShopCode.PAYMENT_FAILURE);
        }
        log.info("订单支付状态修改(->已支付)成功, 更新支付订单状态, payId: {}, 业务ID: {}",
                payment.getPayId(), payment.getOrderId());
        // 创建支付成功 mq 消息
        MqMessageProducer mqMessageProducer = new MqMessageProducer()
                .setId(String.valueOf(idWorker.nextId(null)))
                .setMsgTopic(topic)
                .setMsgTag(tag)
                // payId 已经完成支付, 作为 Message 的 key
                .setMsgKey(String.valueOf(payment.getPayId()))
                .setMsgBody(JSONObject.toJSONString(payment))
                .setMsgStatus(0)
                .setCreateTime(LocalDateTime.now());
        // 持久化 mq 消息到 db
        mqMessageProducerService.save(mqMessageProducer);
        log.info("支付成功, 持久化 mq 消息到 db, payId: {}, 业务ID: {}", payment.getPayId(), payment.getOrderId());
        // 异步发送 MQ 消息（不阻塞 Dubbo 线程，发送结果在回调中处理）
        sendMqMessageAsync(tag, String.valueOf(payment.getPayId()), JSONObject.toJSONString(payment), mqMessageProducer);

        // MQ 消息已持久化，即使发送失败也有补偿任务兜底，直接返回成功
        return Result.success();
    }

    /**
     * 异步发送 MQ 消息
     * 发送成功 → 删除 DB 中的消息记录
     * 发送失败 → 更新 msg_status=2（失败），由 PaymentMqSendCompensateTask 定时补偿
     *
     * @param tag              标签
     * @param key              消息 key
     * @param body             消息体
     * @param mqMessageProducer 消息记录（用于回调中更新 DB 状态）
     */
    private void sendMqMessageAsync(String tag, String key, String body, MqMessageProducer mqMessageProducer) {
        if (StringUtils.isEmpty(topic)) {
            CastException.cast(ShopCode.MQ_TOPIC_IS_EMPTY);
        }
        if (StringUtils.isEmpty(body)) {
            CastException.cast(ShopCode.MQ_MESSAGE_BODY_IS_EMPTY);
        }

        Message message = new Message(topic, tag, key, body.getBytes());
        try {
            rocketMQTemplate.getProducer().send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    if (sendResult.getSendStatus().equals(SendStatus.SEND_OK)) {
                        mqMessageProducerService.removeById(mqMessageProducer.getId());
                        log.info("异步发送MQ消息成功,mqMessageProducer 成功删除记录: payId={}", mqMessageProducer.getMsgKey());
                    } else {
                        mqMessageProducer.setMsgStatus(2);
                        mqMessageProducerService.updateById(mqMessageProducer);
                        log.error("异步发送MQ消息状态异常:mqMessageProducer 没有删除记录 payId={}, status={}",
                                mqMessageProducer.getMsgKey(), sendResult.getSendStatus());
                    }
                }

                @Override
                public void onException(Throwable e) {
                    mqMessageProducer.setMsgStatus(2);
                    mqMessageProducerService.updateById(mqMessageProducer);
                    log.error("异步发送MQ消息异常: payId={}, error={}", mqMessageProducer.getMsgKey(), e.getMessage());
                }
            });
        } catch (Exception e) {
            // 异步发送提交失败 → 立即标记为失败，等待补偿任务重试
            mqMessageProducer.setMsgStatus(2);
            mqMessageProducerService.updateById(mqMessageProducer);
            log.error("异步发送MQ消息提交异常: payId={}, error={}", mqMessageProducer.getMsgKey(), e.getMessage());
        }
    }
}
