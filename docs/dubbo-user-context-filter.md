# Dubbo Filter 用户上下文传递

## 1. 功能概述

在微服务架构中，当用户请求经过多个服务调用时，需要将用户身份信息（userId、userName）自动传递到下游服务。本文档描述了通过 Dubbo Filter 实现用户上下文自动传递的完整方案。

### 1.1 核心功能
- **自动传递**：无需手动传参，用户信息自动跟随 Dubbo RPC 调用传递
- **透明处理**：业务代码无需关心上下文传递逻辑
- **统一位置**：所有 Filter 统一放在 common 模块，便于维护

### 1.2 适用场景
- 订单服务调用商品服务查询商品信息
- 订单服务调用优惠券服务扣减优惠券
- 订单服务调用用户服务扣减余额
- 任何需要通过 Dubbo RPC 传递用户身份的场景

## 2. 实现原理

### 2.1 整体架构

```
用户请求 → Gateway → order-service
                      ↓ (UserInfoInterceptor 设置 UserContext)
                      ↓ (DubboUserContextConsumerFilter 读取 UserContext)
                      ↓ (设置 Dubbo Attachment)
                      ↓
                 coupon-service
                      ↓ (DubboUserContextProviderFilter 读取 Attachment)
                      ↓ (设置 UserContext)
                      ↓ (业务代码通过 UserContext.getUserId() 获取用户信息)
```

### 2.2 核心组件

#### 2.2.1 UserContext（用户上下文）
- **位置**：`common/src/main/java/org/lee/rocket/train/common/context/UserContext.java`
- **作用**：基于 ThreadLocal 存储当前请求的用户信息
- **特点**：线程隔离，请求结束后自动清理

#### 2.2.2 UserInfoInterceptor（HTTP 拦截器）
- **位置**：`common/src/main/java/org/lee/rocket/train/common/interceptor/UserInfoInterceptor.java`
- **作用**：从 HTTP 请求头中读取用户信息，存入 UserContext
- **触发时机**：HTTP 请求到达时

#### 2.2.3 DubboUserContextConsumerFilter（Consumer 端 Filter）
- **位置**：`common/src/main/java/org/lee/rocket/train/common/filter/DubboUserContextConsumerFilter.java`
- **作用**：在 Dubbo 调用发起前，从 UserContext 读取用户信息，放入 Dubbo Attachment
- **激活条件**：`group = "consumer"`，只在调用方生效

#### 2.2.4 DubboUserContextProviderFilter（Provider 端 Filter）
- **位置**：`common/src/main/java/org/lee/rocket/train/common/filter/DubboUserContextProviderFilter.java`
- **作用**：在 Dubbo 请求到达时，从 Attachment 读取用户信息，存入 UserContext
- **激活条件**：`group = "provider"`，只在服务提供方生效

### 2.3 数据流转

```
┌─────────────────────────────────────────────────────────────┐
│ 1. HTTP 请求到达 order-service                              │
│    - UserInfoInterceptor 从 Header 读取 X-User-Id          │
│    - 存入 UserContext (ThreadLocal)                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. order-service 调用 coupon-service (Dubbo RPC)            │
│    - DubboUserContextConsumerFilter 从 UserContext 读取     │
│    - 设置到 Dubbo Attachment: X-User-Id, X-User-Name       │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. coupon-service 接收 Dubbo 请求                           │
│    - DubboUserContextProviderFilter 从 Attachment 读取      │
│    - 存入 UserContext (ThreadLocal)                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 业务代码执行                                             │
│    - UserContext.getUserId() 获取用户 ID                    │
│    - UserContext.getUserName() 获取用户名                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. 请求结束                                                 │
│    - ProviderFilter 在 finally 块中清理 UserContext         │
│    - 防止 ThreadLocal 泄漏                                  │
└─────────────────────────────────────────────────────────────┘
```

## 3. 文件结构

```
common/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/lee/rocket/train/common/
│   │   │       ├── context/
│   │   │       │   └── UserContext.java                    # 用户上下文（ThreadLocal）
│   │   │       ├── interceptor/
│   │   │       │   ├── UserInfoInterceptor.java            # HTTP 拦截器
│   │   │       │   └── FeignUserContextInterceptor.java    # Feign 拦截器
│   │   │       ├── filter/
│   │   │       │   ├── DubboUserContextConsumerFilter.java # Dubbo Consumer Filter
│   │   │       │   └── DubboUserContextProviderFilter.java # Dubbo Provider Filter
│   │   │       └── config/
│   │   │           └── WebMvcConfig.java                   # MVC 配置（注册拦截器）
│   │   └── resources/
│   │       └── META-INF/
│   │           └── dubbo/
│   │               └── org.apache.dubbo.rpc.Filter         # Dubbo SPI 配置
```

## 4. 核心代码详解

### 4.1 UserContext（用户上下文）

```java
package org.lee.rocket.train.common.context;

/**
 * 用户上下文 - 基于 ThreadLocal 存储当前请求的用户信息
 */
public class UserContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_NAME = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setUserName(String userName) {
        USER_NAME.set(userName);
    }

    public static String getUserName() {
        return USER_NAME.get();
    }

    /**
     * 清理上下文，防止 ThreadLocal 泄漏
     */
    public static void clear() {
        USER_ID.remove();
        USER_NAME.remove();
    }
}
```

**关键点**：
- 使用 ThreadLocal 保证线程隔离
- 必须在请求结束时调用 `clear()` 方法清理

### 4.2 DubboUserContextConsumerFilter（Consumer 端）

```java
package org.lee.rocket.train.common.filter;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.lee.rocket.train.common.context.UserContext;

/**
 * Dubbo Consumer 端 Filter - 自动传递用户上下文
 */
@Activate(group = "consumer", order = -1)
public class DubboUserContextConsumerFilter implements Filter {
    
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 从 UserContext 中获取当前用户信息
        Long userId = UserContext.getUserId();
        String userName = UserContext.getUserName();
        
        // 如果用户信息存在，放入 Dubbo Attachment（隐式传参）
        if (userId != null) {
            invocation.setAttachment("X-User-Id", String.valueOf(userId));
        }
        if (userName != null) {
            invocation.setAttachment("X-User-Name", userName);
        }
        
        // 继续执行调用链
        return invoker.invoke(invocation);
    }
}
```

**关键点**：
- `@Activate(group = "consumer", order = -1)`：只在 Consumer 端激活，order 越小优先级越高
- 从 UserContext 读取用户信息，设置到 Dubbo Attachment
- 无需手动清理 UserContext（由 ProviderFilter 负责）

### 4.3 DubboUserContextProviderFilter（Provider 端）

```java
package org.lee.rocket.train.common.filter;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.lee.rocket.train.common.context.UserContext;

/**
 * Dubbo Provider 端 Filter - 自动接收用户上下文
 */
@Activate(group = "provider", order = -1)
public class DubboUserContextProviderFilter implements Filter {
    
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 从 Dubbo Attachment 中获取用户信息
        String userIdStr = invocation.getAttachment("X-User-Id");
        String userName = invocation.getAttachment("X-User-Name");
        
        // 如果用户信息存在，存入 UserContext
        if (userIdStr != null) {
            try {
                Long userId = Long.valueOf(userIdStr);
                UserContext.setUserId(userId);
            } catch (NumberFormatException e) {
                // 忽略无效的用户 ID
            }
        }
        if (userName != null) {
            UserContext.setUserName(userName);
        }
        
        try {
            // 继续执行调用链
            return invoker.invoke(invocation);
        } finally {
            // 请求完成后清理 UserContext，防止 ThreadLocal 泄漏
            UserContext.clear();
        }
    }
}
```

**关键点**：
- `@Activate(group = "provider", order = -1)`：只在 Provider 端激活
- 从 Attachment 读取用户信息，设置到 UserContext
- **必须在 finally 块中清理 UserContext**，防止 ThreadLocal 泄漏

### 4.4 Dubbo SPI 配置

```
# common/src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter
dubboUserContextConsumerFilter=org.lee.rocket.train.common.filter.DubboUserContextConsumerFilter
dubboUserContextProviderFilter=org.lee.rocket.train.common.filter.DubboUserContextProviderFilter
```

**关键点**：
- Dubbo 使用 SPI 机制加载 Filter
- 配置文件必须放在 `META-INF/dubbo/` 目录下
- 文件名必须是 `org.apache.dubbo.rpc.Filter`
- 格式：`filterName=fullClassName`

## 5. 使用示例

### 5.1 业务代码使用

```java
@Service
public class CouponServiceImpl implements ICouponService {
    
    @Override
    public Result<?> reduceCoupon(Coupon coupon) {
        // 直接从 UserContext 获取用户 ID，无需从参数传递
        Long userId = UserContext.getUserId();
        String userName = UserContext.getUserName();
        
        log.info("用户 {} ({}) 正在使用优惠券 {}", userName, userId, coupon.getCouponId());
        
        // 业务逻辑...
        return Result.success();
    }
}
```

### 5.2 调用链路示例

```
1. 用户请求：POST /order/create
   Header: X-User-Id: 1001, X-User-Name: zhangsan

2. order-service 接收请求
   - UserInfoInterceptor 从 Header 读取用户信息
   - UserContext.setUserId(1001)
   - UserContext.setUserName("zhangsan")

3. order-service 调用 coupon-service
   - DubboUserContextConsumerFilter 从 UserContext 读取
   - Attachment: X-User-Id=1001, X-User-Name=zhangsan

4. coupon-service 接收请求
   - DubboUserContextProviderFilter 从 Attachment 读取
   - UserContext.setUserId(1001)
   - UserContext.setUserName("zhangsan")

5. coupon-service 业务代码
   - UserContext.getUserId() → 1001
   - UserContext.getUserName() → "zhangsan"

6. 请求结束
   - DubboUserContextProviderFilter 清理 UserContext
```

## 6. 与 OpenFeign 的对比

| 特性 | Dubbo Filter | OpenFeign Interceptor |
|------|--------------|----------------------|
| 协议 | Dubbo RPC | HTTP |
| 传递方式 | Dubbo Attachment | HTTP Header |
| Consumer 端 | DubboUserContextConsumerFilter | FeignUserContextInterceptor |
| Provider 端 | DubboUserContextProviderFilter | UserInfoInterceptor (HTTP) |
| 性能 | 高性能（二进制协议） | 较低（JSON 序列化） |
| 使用场景 | 内部服务调用 | 跨语言、第三方服务 |

**统一位置**：所有 Filter 都放在 common 模块，便于统一管理。

## 7. 注意事项

### 7.1 ThreadLocal 泄漏问题
- **问题**：ThreadLocal 如果不清理，会导致内存泄漏
- **解决**：ProviderFilter 在 finally 块中调用 `UserContext.clear()`
- **注意**：不要在其他地方手动清理，统一由 ProviderFilter 负责

### 7.2 异步调用问题
- **问题**：Dubbo 异步调用时，ThreadLocal 无法自动传递
- **解决**：需要手动传递上下文
  ```java
  // 保存当前上下文
  Long userId = UserContext.getUserId();
  String userName = UserContext.getUserName();
  
  // 异步调用
  CompletableFuture.supplyAsync(() -> {
      // 手动设置上下文
      UserContext.setUserId(userId);
      UserContext.setUserName(userName);
      try {
          // 业务逻辑
          return doSomething();
      } finally {
          UserContext.clear();
      }
  });
  ```

### 7.3 跨线程问题
- **问题**：使用线程池时，ThreadLocal 无法传递
- **解决**：使用 `TransmittableThreadLocal`（TTL）或手动传递

### 7.4 空指针问题
- **问题**：某些接口不需要用户信息（如公开接口）
- **解决**：业务代码需要判空
  ```java
  Long userId = UserContext.getUserId();
  if (userId != null) {
      // 需要用户信息的逻辑
  } else {
      // 公开接口逻辑
  }
  ```

## 8. 测试验证

### 8.1 单元测试

```java
@Test
public void testUserContextPropagation() {
    // 设置用户上下文
    UserContext.setUserId(1001L);
    UserContext.setUserName("zhangsan");
    
    // 调用 Dubbo 服务
    Coupon coupon = new Coupon();
    coupon.setCouponId(100L);
    Result<?> result = couponService.reduceCoupon(coupon);
    
    // 验证结果
    Assert.assertTrue(result.getSuccess());
}
```

### 8.2 集成测试

```bash
# 启动所有服务
docker-compose up -d

# 发送请求
curl -X POST http://localhost:8080/order/create \
  -H "X-User-Id: 1001" \
  -H "X-User-Name: zhangsan" \
  -H "Content-Type: application/json" \
  -d '{"goodsId": 1, "goodsNumber": 2}'

# 查看日志
tail -f logs/coupon-service.log | grep "用户"
```

## 9. 常见问题

### Q1: 为什么 ConsumerFilter 和 ProviderFilter 都放在 common 模块？
**A**: 为了统一管理。所有用户上下文相关的组件（UserContext、Interceptor、Filter）都放在 common 模块，便于维护和复用。

### Q2: 为什么不使用 Dubbo 的隐式参数自动传递？
**A**: Dubbo 提供了 `RpcContext.getContext().setAttachment()` 方法，但需要手动在每个调用点设置。使用 Filter 可以自动处理，减少重复代码。

### Q3: 如果调用链路很长，会不会有性能问题？
**A**: 不会。Filter 只是在调用前后执行简单的 ThreadLocal 读写操作，性能开销极小（纳秒级）。

### Q4: 如何处理用户信息变更？
**A**: 用户信息在请求开始时设置，整个请求链路中使用。如果需要更新，应该在业务代码中手动更新 UserContext。

## 10. 总结

通过 Dubbo Filter 实现用户上下文自动传递，具有以下优势：

1. **自动化**：无需手动传参，用户信息自动跟随调用链路
2. **透明化**：业务代码无需关心上下文传递逻辑
3. **统一化**：所有 Filter 放在 common 模块，便于维护
4. **安全化**：自动清理 ThreadLocal，防止内存泄漏

这套方案适用于所有需要传递用户身份的微服务调用场景，是微服务架构中的最佳实践。
