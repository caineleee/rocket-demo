package org.lee.rocket.train.order.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.RandomLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * coupon-service 的自定义负载均衡配置（随机策略）
 * <p>
 * 【为什么这么复杂？】
 * Dubbo 只需 @DubboReference(loadbalance = "random") 一行即可切换策略。
 * 但 Spring Cloud LoadBalancer 没有提供类似的简单配置属性，
 * 必须写一个配置类，手动创建 RandomLoadBalancer 实例。
 * <p>
 * 【核心逻辑（就3行）】
 * 1. 获取服务名（coupon-service）
 * 2. 通过工厂获取该服务在 Nacos 中的实例列表
 * 3. 用这些实例创建随机负载均衡器
 * <p>
 * 【重要：不要加 @Configuration 注解】
 * 如果加了 @Configuration，会被 @ComponentScan 扫描到，变成全局策略，影响所有 Feign Client。
 * 不加 @Configuration，通过 @FeignClient(configuration = xxx) 引用时，
 * Spring Cloud 会为它创建独立的子上下文，只对 coupon-service 生效。
 * <p>
 * 【与 Dubbo 负载均衡的对比】
 * - Dubbo：@DubboReference(loadbalance = "random")  ← 一行搞定
 *   示例位置：OrdersServiceImpl 中的 @DubboReference
 * - OpenFeign：需要写配置类 + @FeignClient(configuration = xxx)  ← 比较繁琐
 *   示例位置：CouponFeignClient 的 @FeignClient 注解
 */
public class CouponServiceLoadBalancerConfig {

    /**
     * 创建随机负载均衡器
     * <p>
     * 对比 Dubbo：@DubboReference(loadbalance = "random") 的效果等同于这里的全部代码。
     *
     * @param environment              Spring 环境变量，用于获取服务名
     * @param loadBalancerClientFactory 负载均衡工厂，用于获取服务实例列表
     */
    @Bean
    public ReactorLoadBalancer<ServiceInstance> randomLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        // 1. 获取服务名（即 @FeignClient(name = "coupon-service") 中的 "coupon-service"）
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        // 2+3. 从 Nacos 获取实例列表，创建随机负载均衡器
        return new RandomLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
                name);
    }
}
