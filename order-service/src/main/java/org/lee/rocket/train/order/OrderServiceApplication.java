package org.lee.rocket.train.order;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.lee.rocket.train.common.config.IdWorkerConfig;
import org.lee.rocket.train.order.config.GoodsServiceLoadBalancerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * 订单服务启动类
 * <p>
 * 【OpenFeign 示例】
 * 本启动类同时启用了 Dubbo 和 OpenFeign 两种服务调用方式：
 * <p>
 * 1. @EnableDubbo：启用 Dubbo RPC 调用
 *    - 用于调用内部服务（如 IUserService、ICouponService）
 *    - 高性能、二进制协议、强类型
 *    - 示例：OrdersServiceImpl 中的 @DubboReference private IUserService userService
 * <p>
 * 2. @EnableFeignClients：启用 OpenFeign HTTP 调用
 *    - 用于调用外部服务或跨语言服务
 *    - 通用性强、基于 REST、跨语言
 *    - 示例：OrdersServiceImpl 中的 @Resource private GoodsFeignClient goodsFeignClient
 *    - basePackages：指定扫描 Feign Client 接口的包路径
 * <p>
 * 3. @LoadBalancerClient：为指定服务配置自定义负载均衡策略
 *    - name = "goods-service"：指定目标服务名称
 *    - configuration = GoodsServiceLoadBalancerConfig.class：指定负载均衡配置类
 *    - 对比 Dubbo：@DubboReference(loadbalance = "random") 一行搞定
 *      OpenFeign 需要写配置类 + 这里引用，比较繁琐
 * <p>
 * 【注意事项】
 * - @EnableFeignClients 的 basePackages 必须包含 Feign Client 接口所在的包
 * - 本示例中 Feign Client 在 org.lee.rocket.train.api.feign 包下
 * - @LoadBalancerClient 的 configuration 类不能加 @Configuration 注解
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.lee.rocket.train.order",
    "org.lee.rocket.train.common"
})
@MapperScan("org.lee.rocket.train.order.mapper")
@EnableDubbo
@EnableFeignClients(basePackages = {"org.lee.rocket.train.api.feign"})  // 启用 OpenFeign，扫描 Feign Client 接口
@LoadBalancerClient(name = "goods-service", configuration = GoodsServiceLoadBalancerConfig.class)  // 为 goods-service 配置随机负载均衡策略
@Import(IdWorkerConfig.class)  // 导入 common 模块的 IdWorker 配置
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
