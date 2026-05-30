package org.lee.rocket.train.producer.controller;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.lee.rocket.train.common.constant.RocketMQConstants;
import org.lee.rocket.train.common.model.Result;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final RocketMQTemplate rocketMQTemplate;

    @PostMapping("/send")
    public Result<String> sendMessage(@RequestParam String topic, @RequestBody String message) {
        rocketMQTemplate.convertAndSend(topic, message);
        return Result.success("消息发送成功: " + message);
    }

    @PostMapping("/sendWithKey")
    public Result<String> sendMessageWithKey(@RequestParam String topic, 
                                     @RequestParam String key, 
                                     @RequestBody String message) {

        rocketMQTemplate.send(topic,
                MessageBuilder.withPayload(message)
                        .setHeader(MessageConst.PROPERTY_KEYS, key) // 核心：设置消息索引键
                        .build()
        );

        return Result.success("消息发送成功 (key=" + key + "): " + message);
    }
    
    @PostMapping("/sendDefault")
    public Result<String> sendToDefaultTopic(@RequestBody String message) {
        rocketMQTemplate.convertAndSend(RocketMQConstants.DEFAULT_TOPIC, message);
        return Result.success("消息发送到默认主题: " + message);
    }
}
