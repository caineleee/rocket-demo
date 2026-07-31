package org.lee.rocket.train.payment.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.common.mq.topic.RocketMQConstants;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = RocketMQConstants.DEFAULT_TOPIC,
        consumeMode = ConsumeMode.CONCURRENTLY,
        consumerGroup = "${rocketmq.consumer.group}"
)
public class MessageListener implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        // 收到消息后在此处理业务逻辑（当前为 Demo：仅打印日志）
        log.info("收到消息: {}", message);
    }
}
