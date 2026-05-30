# Common 模块说明

## 📦 模块结构

```
common/
├── pom.xml
└── src/main/
    ├── java/org/lee/rocket/train/common/
    │   ├── RocketMQConstants.java      # RocketMQ 常量
    │   ├── constant/
    │   │   └── CommonConstants.java    # 通用常量
    │   └── model/
    │       └── Result.java             # 通用响应结果
    └── resources/
        └── application.properties      # 公共配置
```

## 🎯 功能说明

### 1. 常量管理

#### RocketMQConstants
```java
// Topic 定义
RocketMQConstants.DEFAULT_TOPIC      // "test-topic"
RocketMQConstants.ORDER_TOPIC        // "order-topic"

// 消费者组
RocketMQConstants.PRODUCER_GROUP     // "producer-group"
RocketMQConstants.CONSUMER_GROUP     // "consumer-group"

// NameServer
RocketMQConstants.NAME_SERVER        // "localhost:9876"
```

#### CommonConstants
```java
// 编码和格式
CommonConstants.UTF8                 // "UTF-8"
CommonConstants.JSON_CONTENT_TYPE    // "application/json"

// 响应码
CommonConstants.SUCCESS_CODE         // "200"
CommonConstants.ERROR_CODE           // "500"
```

### 2. 通用模型

#### Result<T>
统一的 API 响应结果封装：

```java
// 成功响应（带数据）
return Result.success(data);

// 成功响应（无数据）
return Result.success();

// 失败响应
return Result.error("错误信息");
```

### 3. 公共配置

`application.properties` 中包含：
- Docker Compose 生命周期管理配置
- 其他跨模块共享的配置

## 🔗 依赖引用

在 `producer` 或 `consumer` 模块的 `pom.xml` 中已自动包含：

```xml
<dependency>
    <groupId>org.lee.rocket.train</groupId>
    <artifactId>common</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 💡 使用示例

### Producer 中使用
```java
@RestController
public class MessageController {
    
    @PostMapping("/send")
    public Result<String> sendMessage(@RequestBody String message) {
        // 使用常量
        rocketMQTemplate.convertAndSend(RocketMQConstants.DEFAULT_TOPIC, message);
        
        // 使用统一响应
        return Result.success("消息发送成功: " + message);
    }
}
```

### Consumer 中使用
```java
@Component
@RocketMQMessageListener(
    topic = RocketMQConstants.DEFAULT_TOPIC,
    consumerGroup = RocketMQConstants.CONSUMER_GROUP
)
public class MessageListener implements RocketMQListener<String> {
    
    @Override
    public void onMessage(String message) {
        log.info("收到消息: {}", message);
    }
}
```

## 📝 扩展建议

当需要添加新的公共内容时：

1. **新增常量类** → `constant/` 目录
2. **新增 DTO/VO** → `model/` 或 `dto/` 目录
3. **新增工具类** → `util/` 目录
4. **新增枚举** → `enums/` 目录
5. **新增异常类** → `exception/` 目录

保持模块化组织，便于维护和使用。
