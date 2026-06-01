package org.lee.rocket.train.consumer.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.common.constant.RocketMQConstants;
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
        log.info("收到消息: {}", message);
        // 在这里处理接收到的消息
        System.out.println("处理消息: " + message);
    }
}
