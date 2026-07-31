package org.lee.rocket.train.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway 服务启动类
 *
 * 【Gateway 的作用】
 * 1. 统一入口：所有外部请求都通过 Gateway 进入微服务集群
 * 2. 路由转发：根据请求路径将请求转发到对应的微服务
 * 3. 认证鉴权：统一处理 JWT 认证，避免每个服务重复实现
 * 4. 跨域处理：统一处理 CORS，避免每个服务重复配置
 * 5. 安全防护：统一处理 XSS 过滤等安全问题
 *
 * 【为什么用 Gateway 而不是在每个服务中处理？】
 * - 集中管理：认证、跨域、安全等逻辑只需写一次
 * - 性能更好：避免重复处理，减少网络开销
 * - 易于维护：修改逻辑只需改 Gateway，不用改所有服务
 * - 大厂做法：阿里、美团、字节都用 API Gateway 作为统一入口
 *
 * 【注意事项】
 * - Gateway 基于 WebFlux（响应式编程），不能使用 spring-boot-starter-web
 * - Filter 要用 GlobalFilter，不能用 Servlet Filter
 * - 不能使用 @EnableDubbo（Gateway 不参与 Dubbo 调用）
 *
 * 【日志】
 * 启动横幅使用 SLF4J（通过 Lombok @Slf4j 注入 log 字段）输出，替代 System.out。
 */
// 排除 DataSource 自动装配：Gateway 基于 WebFlux 仅依赖 Redis（响应式），不连接数据库；
// 但 common 模块（编译期依赖）引入了 mybatis-plus-spring-boot3-starter，会传递性触发
// DataSourceAutoConfiguration，因 Gateway 无任何数据源配置而以 "Failed to determine a suitable driver class" 启动失败
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDiscoveryClient  // 启用服务发现（从 Nacos 获取服务列表）
@Slf4j
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
        log.info("========================================");
        log.info("Gateway Service 启动成功！");
        log.info("Gateway 地址: http://localhost:8080");
        log.info("========================================");
    }
}
