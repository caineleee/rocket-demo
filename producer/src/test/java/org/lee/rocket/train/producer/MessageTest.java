package org.lee.rocket.train.producer;

import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.lee.rocket.train.common.constant.RocketMQConstants;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @ClassName MessageTest
 * @Description
 * @Author lihongliang
 * @Date 2026/5/31 22:21
 * @Version 1.0
 */
@SpringBootTest
public class MessageTest {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Test
    public void testSendMessage() {

//        System.out.println("当前配置的NameServer地址：" + environment.getProperty("rocketmq.name-server"));

        rocketMQTemplate.convertAndSend(RocketMQConstants.DEFAULT_TOPIC, "Hello, RocketMQ!");
    }
}
