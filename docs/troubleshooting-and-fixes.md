

## 问题总览

整个排查过程按时间顺序分为三个阶段，共 **11 个问题**：

| 阶段 | 问题数 | 核心痛点 |
|------|--------|----------|
| 一、启动阶段（服务起不来） | 7 个 | 依赖注入、序列化、JWT、自动装配、路由 |
| 二、接口测试阶段（接口不通） | 3 个 | 日志配置、状态码误用、网络漂移 |
| 三、日志治理（磁盘撑爆） | 1 个专题 | 无界日志输出 |

```
问题依赖关系图：

启动阶段                          接口测试阶段
─────────                        ────────────
①NPE ──┐
②序列化 ┤(掩盖①)                  ⑧logback类路径 ──→ ⑨common未install
③JWT密钥┤
④数据源 ┤
⑤白名单┤                          ⑩is_paid状态码
⑥路由错┤                          ⑪HOST_IP漂移
⑦表名 ─┘
        │
        └──→ 全部服务启动成功 ──→ 接口跑通
```

---

## 一、启动阶段问题（服务起不来）

### 问题 1：登录接口 NPE — TokenService 的 redisTemplate 为 null

#### 现象
`POST /user/login` 返回 500，日志报：
```
java.lang.NullPointerException: Cannot invoke 'StringRedisTemplate.opsForValue()' 
because 'this.redisTemplate' is null
```

#### 根因分析
[TokenService.java](file:///Users/lihongliang/IdeaProjects/rocket-demo/common/src/main/java/org/lee/rocket/train/common/service/TokenService.java) 使用 Lombok 的 `@RequiredArgsConstructor` 做构造器注入，但 `redisTemplate` 字段**漏加了 `final` 关键字**：

```java
// ❌ 错误写法（修复前）
@RequiredArgsConstructor
public class TokenService {
    private StringRedisTemplate redisTemplate;  // 没有 final！
}
```

**关键机制**：`@RequiredArgsConstructor` 只为 **`final` 字段**（或 `@NonNull` 字段）生成构造器参数。字段没有 `final`，就不会出现在构造器参数列表里，Spring 自然不会注入它 → 运行时该字段为 `null` → 调用时 NPE。

#### 修复方法
给字段加 `final`：
```java
// ✅ 正确写法（修复后）
@RequiredArgsConstructor
public class TokenService {
    private final StringRedisTemplate redisTemplate;  // 加 final，构造器才会注入
}
```

#### 经验教训
- 用 `@RequiredArgsConstructor` 时，**所有需要注入的字段必须加 `final`**，这是最容易踩的 Lombok 坑
- NPE 发生在依赖注入的字段上时，第一反应应该是检查「字段是否真的被注入了」——而不是检查业务逻辑

---

### 问题 2：JSON 序列化失败 — EncodingFilter 错误设置 Content-Type

#### 现象
登录接口 500，报：
```
org.springframework.web.HttpMessageNotWritableException: 
No converter for [class org.lee.rocket.train.common.model.Result] 
with preset Content-Type 'text/html;charset=UTF-8'
```

#### 根因分析
[EncodingFilter.java](file:///Users/lihongliang/IdeaProjects/rocket-demo/common/src/main/java/org/lee/rocket/train/common/filter/EncodingFilter.java) 里强制把响应的 Content-Type 设成了 `text/html`：

```java
// ❌ 错误写法（修复前）
httpResponse.setContentType("text/html;charset=UTF-8");
```

Spring MVC 返回 `Result` 对象时，会根据 **Content-Type** 查找匹配的 `HttpMessageConverter`。Jackson 的 `MappingJackson2HttpMessageConverter` 只处理 `application/json` 等 JSON 类型，**不处理 `text/html`** → 找不到 converter → 抛 `HttpMessageNotWritableException`。

#### 修复方法
只设置字符编码，不强制设 Content-Type（让 Spring MVC 自己根据返回类型协商）：

```java
// ✅ 正确写法（修复后）
httpResponse.setCharacterEncoding("UTF-8");
```

#### ⚠️ 注意：这个错误掩盖了问题 1
问题 1 的 NPE 和问题 2 的序列化失败**都导致登录 500**。NPE 发生在 Controller 调用 TokenService 时，序列化失败发生在返回 Result 时。报错信息只显示了序列化异常，**掩盖了真正的 NPE**。必须**按依赖顺序修**：先修 NPE（问题 1），再修序列化（问题 2）。

#### 经验教训
- Filter 里**不要随意改 Content-Type**，那会破坏 Spring MVC 的内容协商机制
- `setCharacterEncoding` 和 `setContentType` 是两回事：前者只设编码，后者会覆盖整个 Content-Type
- 多个问题导致同一症状时，报错信息可能只显示其中一个，要按调用链顺序逐个排查

---

### 问题 3：JWT 密钥长度不足 — WeakKeyException

#### 现象
`JwtUtil` 静态初始化直接失败，服务起不来：
```
io.jsonwebtoken.security.WeakKeyException: 
The specified key byte array is 248 bits which is not secure enough. 
The signing key's size is 248 bits which is not secure enough 
for HMAC-SHA256. JWT keys must be at least 256 bits long.
```

#### 根因分析
JWT 用 HMAC-SHA256 算法签名，jjwt 库要求密钥**必须 >= 32 字节（256 位）**。当时配置的密钥只有 31 字节（248 位），不满足安全要求。

#### 修复方法
把密钥扩展到 38 字节（满足 >= 32 字节要求）：
```yaml
# application.yml
jwt:
  secret: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx  # 38 字节
```

#### 经验教训
- HMAC-SHA256 的密钥**硬性要求 >= 32 字节**，差一个字节都不行
- 密钥要从配置文件读（外置），不要硬编码在代码里
- `Keys.hmacShaKeyFor()` 在密钥太短时会直接抛异常，且发生在静态初始化块里 → 服务启动就失败

---

### 问题 4：Gateway 启动失败 — DataSourceAutoConfiguration 误触发

#### 现象
gateway-service 启动报：
```
Failed to determine a suitable driver class
```

#### 根因分析
gateway 依赖了 `common` 模块，而 common 引入了 `mybatis-plus` 依赖。Spring Boot 的自动装配机制发现 classpath 里有 MyBatis 相关类，就触发了 `DataSourceAutoConfiguration`，尝试自动配置数据源。但 **gateway 不需要数据库**，没有配数据源 → 自动装配失败。

```
依赖链：
gateway-service → common → mybatis-plus → 触发 DataSourceAutoConfiguration
                                         → gateway 没有数据源配置 → 启动失败
```

#### 修复方法
在 [GatewayServiceApplication.java](file:///Users/lihongliang/IdeaProjects/rocket-demo/gateway-service/src/main/java/org/lee/rocket/train/gateway/GatewayServiceApplication.java) 排除数据源自动装配：

```java
// ✅ 排除不需要的自动装配
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class GatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
```

#### 经验教训
- Spring Boot 自动装配是「按 classpath 内容决定装不装」的——只要依赖里有相关类就会触发，不管你是不是真的需要
- **Gateway 底层是 WebFlux**，不碰数据库，但被 common 拖进来的 mybatis-plus 连累了
- 排查 `Failed to determine a suitable driver class` 时，要想清楚「这个服务到底需不需要数据源」——不需要就 exclude

---

### 问题 5：Token 刷新接口 401 — 未加入 JWT 白名单

#### 现象
`POST /user/refresh` 返回 401：
```
Missing or invalid Authorization header
```

#### 根因分析
[JwtAuthGlobalFilter.java](file:///Users/lihongliang/IdeaProjects/rocket-demo/gateway-service/src/main/java/org/lee/rocket/train/gateway/filter/JwtAuthGlobalFilter.java) 的 JWT 白名单里**没有 `/user/refresh`**。

这个接口的特殊性：它是 **Access Token 过期后**才调用的，目的是用 Refresh Token 换新的 Access Token。调用时客户端**根本拿不出有效的 Access Token**（已经过期了），所以网关拦截 → 401。这就形成死循环：要刷新 Token 就得过 JWT 认证，但要过认证又得有有效 Token。

#### 修复方法
把 `/user/refresh` 加入白名单：

```java
private static final List<String> WHITE_LIST = List.of(
        "/user/login",
        "/user/refresh",   // ← 加这个：Token 刷新接口必须放行
        "/goods/**",
        "/coupon/**"
);
```

白名单匹配用 `AntPathMatcher`（不能用 `startsWith`）：

```java
private final AntPathMatcher pathMatcher = new AntPathMatcher();

private boolean isWhiteListed(String path) {
    // ✅ 用 AntPathMatcher 支持 /goods/** 这类 Ant 模式
    return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
}
```

#### 经验教训
- JWT 白名单的判断标准：**这个接口被调用时，客户端是否可能持有有效 Token**。如果不能（登录、刷新、公开接口），就必须放行
- 白名单匹配**必须用 `AntPathMatcher`**，`startsWith` 遇到 `/goods/**` 这种 Ant 模式就匹配不上
- 想清楚「鸡生蛋还是蛋生鸡」：刷新 Token 的接口本身不能要求 Token

---

### 问题 6：Gateway 路由到 Dubbo RPC 端口 — invalid version format

#### 现象
gateway 调 user-service **间歇性返回 500**（大概一半概率失败）：
```
invalid version format: UNSUPPORTED
```

#### 根因分析（两个问题叠加）

**问题 A：user-service 没注册 HTTP 端口到 Nacos**

user-service 只配了 Dubbo 注册中心（`dubbo.registries.nacos`），**没配 Spring Cloud Nacos Discovery**。Dubbo 注册的是 RPC 端口（20880），不是 HTTP 端口（8080）。Gateway 通过 `lb://user-service` 路由时，从 Nacos 拿到的实例只有 RPC 端口 → HTTP 请求打到 RPC 端口 → 报 `invalid version format`（Dubbo 协议解析不了 HTTP 请求）。

**问题 B：Dubbo 应用名和 Spring Cloud 应用名一样**

两个应用名都叫 `user-service`，导致 Dubbo RPC 实例和 Spring Cloud HTTP 实例**注册到同一个服务名下**。Gateway 的 LoadBalancer 随机选一个实例，**大概 50% 概率选中 RPC 端口** → 请求失败。

```
Nacos 服务列表（修复前）：
  服务名: user-service
    实例1: 192.168.x.x:8080  (Spring Cloud HTTP)  ← 50% 命中，正常
    实例2: 192.168.x.x:20880 (Dubbo RPC)          ← 50% 命中，500 错误
```

#### 修复方法

**修复 A**：加 Nacos Discovery 依赖 + 配置，注册 HTTP 端口：
```yaml
# user-service application.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}  # 注册 HTTP 端口
```

**修复 B**：Dubbo 应用名加 `-dubbo` 后缀，跟 Spring Cloud 应用名分开：
```yaml
dubbo:
  application:
    name: user-service-dubbo   # ← 跟 spring.application.name=user-service 分开
```

#### 怎么验证 HTTP 端口注册上了？
去 Nacos 控制台看实例的 metadata，**有 `preserved.register.source=SPRING_CLOUD` 才说明 HTTP 端口注册上了**。光看服务列表里有名字是不够的（Dubbo 也会注册）。

#### 经验教训
- **Dubbo 注册中心 ≠ Spring Cloud 注册中心**：Dubbo 注册的是 RPC 端口，Spring Cloud Discovery 注册的是 HTTP 端口，两个要分开配
- 应用名**绝对不能一样**，否则两个实例混在一个服务名下，LoadBalancer 会随机命中错误的端口
- 间歇性 500（时好时坏）通常是「多个实例里有对有错」的信号，要想到负载均衡选实例的问题

---

### 问题 7：支付创建接口 500 — Payment 实体表名映射错误

#### 现象
支付创建接口 `PUT /payment/create` 返回 500，INSERT 失败。

#### 根因分析
[Payment.java](file:///Users/lihongliang/IdeaProjects/rocket-demo/service-pojo/src/main/java/org/lee/rocket/train/service/entity/Payment.java) 的 `@TableName` 注解写错了表名：

```java
// ❌ 错误：注解里的表名跟数据库实际表名不一致
@TableName("tb_order_payment")   // 数据库里实际是 tb_payment
public class Payment implements Serializable { ... }
```

MyBatis-Plus 根据 `@TableName` 生成 SQL：`INSERT INTO tb_order_payment ...`，但数据库里没有 `tb_order_payment` 表 → 报错。

#### 修复方法
```java
// ✅ 正确：跟数据库表名对上
@TableName("tb_payment")
public class Payment implements Serializable { ... }
```

#### 经验教训
- `@TableName` 的值必须**跟数据库物理表名一字不差**
- INSERT/UPDATE 报「表不存在」时，第一时间检查 `@TableName` 注解

---

## 二、接口测试阶段问题（接口不通）

### 问题 8：pay-service 启动失败 — logback ThresholdFilter 类路径错误

#### 现象
pay-service 启动直接失败：
```
java.lang.IllegalStateException: Logback configuration error detected:
ERROR in ch.qos.logback.core.model.processor.ImplicitModelHandler - 
Could not create component [filter] of type [ch.qos.logback.core.filter.ThresholdFilter]
java.lang.ClassNotFoundException: ch.qos.logback.core.filter.ThresholdFilter
```

#### 根因分析
[logback-spring.xml](file:///Users/lihongliang/IdeaProjects/rocket-demo/common/src/main/resources/logback-spring.xml) 里 CONSOLE appender 的 filter 类路径写错了：

```xml
<!-- ❌ 错误：logback.core 包下没有 ThresholdFilter -->
<filter class="ch.qos.logback.core.filter.ThresholdFilter">
    <level>INFO</level>
</filter>
```

`ThresholdFilter` 这个类在 **logback-classic** 模块里，正确的包名是 `ch.qos.logback.classic.filter.ThresholdFilter`，**不在 logback-core**。

#### 修复方法
```xml
<!-- ✅ 正确：classic 包下才有 ThresholdFilter -->
<filter class="ch.qos.logback.classic.filter.ThresholdFilter">
    <level>INFO</level>
</filter>
```

#### 经验教训
- logback 分两个模块：**logback-core**（核心，Appender、RollingPolicy 等基础设施）和 **logback-classic**（经典，Filter、Logger、Level 等上层功能）
- `ThresholdFilter`、`LevelFilter` 这些 Filter 都在 **classic** 包下，不在 core
- logback 配置报错会让服务**直接起不来**（发生在 Spring 初始化日志系统阶段，比业务代码还早）

---

### 问题 9：源码改了但不生效 — common 模块没重新 install

#### 现象
问题 8 修完源码后，pay-service 重启**还是报同样的错**（`ch.qos.logback.core.filter.ThresholdFilter`），源码明明已经改对了。

#### 根因分析
各微服务通过 `mvn spring-boot:run` 独立启动（不带 `-am` 参数），**不会重新构建依赖模块**。它用的是 `~/.m2/repository` 里 common 模块的 jar 包：

```
源码（src/main/resources）  →  已改对 ✅
common/target/classes      →  已改对 ✅（本地编译输出）
~/.m2/.../common-0.0.1.jar →  还是旧的 ❌（没重新 install）
                                   ↑
                            mvn spring-boot:run 用的是这个！
```

#### 修复方法
在 common 模块下执行 `mvn install`，把新代码推到 ~/.m2：
```bash
cd common
mvn install -DskipTests
# 之后再启动 pay-service，就能用上新配置了
```

验证 jar 内容已更新：
```bash
unzip -p ~/.m2/.../common-0.0.1.jar logback-spring.xml | grep ThresholdFilter
# 应输出: ch.qos.logback.classic.filter.ThresholdFilter（正确）
```

#### 经验教训
- `mvn spring-boot:run` 不带 `-am` **不会重建依赖模块**，用的是 ~/.m2 里的旧产物
- 改了 common 这种被依赖的模块后，**必须 `mvn install`**，否则改动不生效
- 排查「源码对了但运行时还报旧值」时，去对比 ~/.m2 jar 里的内容跟源码，往往就是 jar 没更新

---

### 问题 10：支付回调 500 — is_paid 错用 API 响应码导致 Data Truncation

#### 现象
`PUT /payment/callback` 返回 500，日志报：
```
org.springframework.dao.DataIntegrityViolationException:
### Error updating database. Cause: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation:
Data truncation: Out of range value for column 'is_paid' at row 1
### SQL: UPDATE tb_payment SET order_id=?, is_paid=? WHERE pay_id=?
```

#### 根因分析
[PaymentServiceImpl.java](file:///Users/lihongliang/IdeaProjects/rocket-demo/pay-service/src/main/java/org/lee/rocket/train/payment/service/impl/PaymentServiceImpl.java) 的 `callbackPayment` 方法里，`setIsPaid` 用错了 ShopCode：

```java
// ❌ 错误：PAYMENT_IS_PAID 是 API 响应码，值 = 70002
pay.setIsPaid(ShopCode.PAYMENT_IS_PAID.getCode());  // 写入 70002
```

数据库 `is_paid` 列是 `tinyint(1)`（范围 -128~127），**存不下 70002** → MySQL 报 Data truncation。

**ShopCode 枚举混了两类语义**：

| 类型 | 标志 | 值范围 | 用途 | 例子 |
|------|------|--------|------|------|
| DB 状态码 | 构造器第一个参数 `true` | 小（0/1/2） | 写进数据库 | `ORDER_PAY_STATUS_IS_PAY(true, 2, "订单已付款")` |
| API 响应码 | 构造器第一个参数 `false` | 大（70001+） | 抛业务异常给前端 | `PAYMENT_IS_PAID(false, 70002, "支付订单已支付")` |

`PAYMENT_IS_PAID(70002)` 是**响应码**，本来是给 `CastException.cast()` 抛异常用的（「这个订单已经支付过了」），不是用来写库的。

#### 修复方法
改用 DB 状态码 `ORDER_PAY_STATUS_IS_PAY(2)`：

```java
// ✅ 正确：用 DB 状态码，与 createPayment 用 NO_PAY(0) 对称
pay.setIsPaid(ShopCode.ORDER_PAY_STATUS_IS_PAY.getCode());  // 写入 2
```

这跟 `createPayment` 里用 `ORDER_PAY_STATUS_NO_PAY(0)` 是对称的，也跟回调请求 `isPaid=2` 的语义一致。

#### 经验教训
- 枚举里混了两类语义时，**写库只能用状态码**，响应码只能给异常抛出用
- Data truncation 报错 → 先查写入的值是不是超出了列类型范围（tinyint 只能存 -128~127）
- 看枚举设计要看构造器参数的含义，`true`/`false` 不是随便填的

---

### 问题 11：MQ 发送失败 — HOST_IP 网段漂移导致 broker 不可达

#### 现象
支付回调返回 200（主流程成功），但**异步发送 MQ 消息失败**：
```
ERROR - 异步发送MQ消息异常: payId=2082883489870516225, error=unknown reason
```
`tb_mq_message_producer` 表里记录的 `msg_status=2`（失败）。

#### 根因分析
宿主机从 `192.168.31.x` 网段切换到了 `192.168.5.x` 网段，但 [.env](file:///Users/lihongliang/IdeaProjects/rocket-demo/.env) 里的 `HOST_IP` 没同步更新：

```
.env: HOST_IP=192.168.31.97   ← 旧网段（已不可达）
宿主机实际 IP: 192.168.5.93   ← 新网段
```

**故障链路**：

```
1. broker 启动时从 .env 读 HOST_IP，用 sed 写进 broker.conf：
   brokerIP1 = 192.168.31.97  （旧 IP）

2. broker 把这个 IP 注册到 nameserver：
   "我是 broker-a，地址是 192.168.31.97:10911"

3. 宿主机上的 pay-service 连 nameserver 拿 broker 地址：
   nameserver 返回 192.168.31.97:10911

4. pay-service 尝试连 192.168.31.97:10911 → 不可达 → 发送失败
   报 "unknown reason"（注意：不是 "No route info"）
```

**为什么报 "unknown reason" 而不是 "No route info"？**
- "No route info" = nameserver 里根本没有这个 topic 的路由信息
- "unknown reason" = 能从 nameserver 拿到 broker 地址，但连上去发送时失败了（broker 地址是旧 IP，连不上）

#### 修复方法
1. 更新 .env 的 HOST_IP 和 NACOS_SERVER_ADDR：
```env
HOST_IP=192.168.5.93
NACOS_SERVER_ADDR=192.168.5.93:8848
```

2. 重建 broker 容器（让它重新注册新 IP）：
```bash
docker compose up -d --force-recreate broker-a-master
```

3. 重启 pay-service（重新从 nameserver 拿新 broker 地址）

**注意**：Seata 和 Nacos 容器**不用重启**——它们用 Docker 服务名（`nacos:8848`）通信，不走 HOST_IP。只有 broker 用 HOST_IP（通过 sed 注入 brokerIP1）。

#### 修复后验证
```
broker 注册信息: 192.168.5.93:10911  ✅
宿主机可达:      nc -z 192.168.5.93 10911 → succeeded  ✅
MQ 发送:         "异步发送MQ消息成功，记录已删除"  ✅
```

#### 经验教训
- 宿主机换网络（切 WiFi、换地方）后，`.env` 里的 IP 会失效，broker 注册的旧 IP 不可达
- "unknown reason" ≠ "No route info"：前者是 broker 连不上，后者是 topic 路由找不到——排查方向不同
- Docker 容器间通信用**服务名**（`nacos:8848`、`namesrv-1:9876`），宿主机服务通信用 **localhost + 端口映射**——只有 broker 的 `brokerIP1` 是暴露给宿主机的，会受 HOST_IP 影响

---

## 三、日志治理专题 — 磁盘被撑爆 20GB

### 现象
6 个微服务同时跑时，本地磁盘被日志撑爆约 20GB，导致命令都执行不了。

### 根因（三个叠加）

**1. 网关开了 DEBUG 日志**
gateway 配置里 `org.springframework.cloud.gateway` 等开了 DEBUG，每个请求都打印详细路由日志，量大。

**2. MyBatis 用 StdOutImpl 直写 stdout**
```yaml
# ❌ StdOutImpl 直接 System.out.println，绕过 logback，不受控
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```
每条 SQL 都直接打印到 stdout，被后台进程捕获到无界的 job 日志文件里。

**3. Dubbo 缓存锁重试循环**
某个 Dubbo 配置问题导致缓存锁一直重试，高频刷 ERROR 日志，且日志无总量上限。

### 治理方案

创建共享 [logback-spring.xml](file:///Users/lihongliang/IdeaProjects/rocket-demo/common/src/main/resources/logback-spring.xml)，所有微服务复用，核心措施：

```xml
<!-- 1. 滚动文件，总量上限 200MB（超出自动删最旧）—— 磁盘防护的核心 -->
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/${appName}.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>logs/${appName}.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
        <maxFileSize>10MB</maxFileSize>
        <maxHistory>7</maxHistory>
        <totalSizeCap>200MB</totalSizeCap>   <!-- ← 关键：总量有界 -->
    </rollingPolicy>
</appender>

<!-- 2. 框架包统一 WARN，砍掉 DEBUG/INFO 刷屏 -->
<logger name="org.apache.dubbo" level="WARN"/>
<logger name="com.alibaba.nacos" level="WARN"/>
<logger name="org.springframework" level="WARN"/>
<!-- ... -->

<!-- 3. 业务包保持 INFO，调试信息可见 -->
<logger name="org.lee.rocket.train" level="INFO"/>
```

MyBatis 日志实现改用 Slf4jImpl（走 logback，受 totalSizeCap 保护）：
```yaml
# ✅ Slf4jImpl 走 logback，受 totalSizeCap 上限保护
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

运维约定：
- `spring-boot:run` 时 stdout 重定向到 `/dev/null`（`> /dev/null 2>&1`），调试只看有界的 `logs/<service>.log`
- "报错即停服务"只作调试习惯，磁盘防护完全靠 `totalSizeCap` 自动兜底

### 经验教训
- `totalSizeCap` 是日志防爆炸的**终极保险**——即使某个框架陷入无限重试，日志也最多 200MB
- `StdOutImpl` 是日志失控的元凶之一，它绕过 logback 直写 stdout，**必须换成 `Slf4jImpl`**
- DEBUG 日志只在临时调试时开（用启动参数 `--logging.level.xxx=DEBUG`），用完即撤，不要长期留在配置里

---

## 附录：问题速查表

按报错关键词快速定位：

| 报错关键词 | 对应问题 | 一句话修复 |
|-----------|---------|-----------|
| `NullPointerException ... redisTemplate is null` | 问题 1 | 注入字段加 `final` |
| `HttpMessageNotWritableException ... text/html` | 问题 2 | EncodingFilter 别设 Content-Type，只设编码 |
| `WeakKeyException ... 248 bits` | 问题 3 | JWT 密钥 >= 32 字节 |
| `Failed to determine a suitable driver class` | 问题 4 | 排除 `DataSourceAutoConfiguration` |
| `Missing or invalid Authorization header`（刷新接口） | 问题 5 | `/user/refresh` 加入白名单 |
| `invalid version format: UNSUPPORTED` | 问题 6 | 加 Nacos Discovery + Dubbo 应用名加后缀 |
| INSERT 表不存在 | 问题 7 | `@TableName` 跟物理表名对上 |
| `ClassNotFoundException ... ThresholdFilter` | 问题 8 | 类路径改成 `classic.filter` |
| 源码改了不生效 | 问题 9 | `mvn install` common 模块 |
| `Data truncation: Out of range value` | 问题 10 | 别用响应码写库，用状态码 |
| MQ `unknown reason` | 问题 11 | 检查 HOST_IP 是否跟当前网段一致 |
| 磁盘被日志撑爆 | 日志治理 | totalSizeCap + Slf4jImpl + 框架包 WARN |

---

## 排查方法论小结

这 11 个问题的排查过程，体现了几个通用的排查思路：

1. **看完整报错栈**：报错信息的首行（异常类型+消息）是根因，不要只看堆栈尾部
2. **按依赖顺序修**：多个问题导致同一症状时（如问题 1+2 都导致登录 500），按调用链顺序逐个修，否则后面的错误会掩盖前面的
3. **区分「源码」和「运行时产物」**：源码对了不等于运行时用对了——Maven 依赖、~/.m2 jar、target/classes 是三套东西（问题 9）
4. **间歇性失败想负载均衡**：时好时坏通常是「多个实例里有对有错」——想到注册了多个端口/实例（问题 6）
5. **环境漂移要警惕**：换网络后 IP 会变，broker/Nacos 注册的旧 IP 会失效（问题 11）
6. **有界优于无界**：日志、缓存等「会增长」的东西，都要设上限（日志治理）
