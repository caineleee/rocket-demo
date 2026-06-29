package org.lee.rocket.train.payment.listnener;

import lombok.extern.apachecommons.CommonsLog;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

/**
 * @ClassName orderFailure
 * @Description 订单失败监听器
 * @Author lihongliang
 * @Date 2026/6/29 15:25
 * @Version 1.0
 */
@CommonsLog
@RocketMQMessageListener(
        topic = "${mq.topics.order-failure}",
        consumerGroup = "${rocketmq.consumer.order-failure.group}",
        messageModel = MessageModel.BROADCASTING) // 广播模式
public class orderFailure implements RocketMQListener<MessageExt> {

    @Override
    public void onMessage(MessageExt messageExt) {

    }
}
