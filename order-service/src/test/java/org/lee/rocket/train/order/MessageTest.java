package org.lee.rocket.train.order;

import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.lee.rocket.train.common.mq.topic.RocketMQConstants;
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
        // 注意：此测试需要连接到配置的 RocketMQ NameServer (192.168.2.74:9876)
        // 如果无法连接，请确保：
        // 1. 网络可达
        // 2. RocketMQ 服务正在运行
        // 3. 防火墙允许访问
        
//        System.out.println("当前配置的NameServer地址：" + environment.getProperty("rocketmq.name-server"));

        rocketMQTemplate.convertAndSend(RocketMQConstants.DEFAULT_TOPIC, "Hello, RocketMQ!");
    }
}
