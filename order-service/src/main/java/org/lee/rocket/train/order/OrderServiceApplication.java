package org.lee.rocket.train.order;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.lee.rocket.train.common.config.IdWorkerConfig;
import org.lee.rocket.train.config.FeignBasicLogLevel;
import org.lee.rocket.train.order.config.CouponServiceLoadBalancerConfig;
import org.lee.rocket.train.order.config.GoodsServiceLoadBalancerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

// 订单服务启动类注解
@SpringBootApplication
// 扫描 order-service 模块和 common 公共模块下的组件
@ComponentScan(basePackages = {
    "org.lee.rocket.train.order",
    "org.lee.rocket.train.common"
})
// 扫描 order-service 模块下的 Mapper 接口
@MapperScan("org.lee.rocket.train.order.mapper")
// 启用 Dubbo RPC 调用
@EnableDubbo
// 启用 OpenFeign HTTP 调用
@EnableFeignClients(basePackages = "org.lee.rocket.train.api.feign", defaultConfiguration = FeignBasicLogLevel.class)  // 启用 OpenFeign，扫描 Feign Client 接口
// 为被调服务配置自定义负载均衡策略（多个服务用 @LoadBalancerClients 包裹）
@LoadBalancerClients({
    @LoadBalancerClient(name = "goods-service", configuration = GoodsServiceLoadBalancerConfig.class),   // goods-service：随机策略
    @LoadBalancerClient(name = "coupon-service", configuration = CouponServiceLoadBalancerConfig.class)  // coupon-service：随机策略
})
@Import(IdWorkerConfig.class)  // 导入 common 模块的 IdWorker 配置
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
