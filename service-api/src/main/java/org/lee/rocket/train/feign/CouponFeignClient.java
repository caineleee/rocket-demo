package org.lee.rocket.train.feign;

import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.config.FeignHeadersLogLevel;
import org.lee.rocket.train.service.entity.Coupon;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 优惠券服务 Feign Client（HTTP 调用示例）
 * <p>
 * 【OpenFeign 示例说明】
 * 这是一个 OpenFeign Client 接口，用于通过 HTTP 调用 coupon-service 的 REST API。
 * <p>
 * 【与 Dubbo 的对比】
 * - Dubbo 调用：使用 @DubboReference 注入 ICouponService 接口（RPC 协议，高性能）
 *   示例位置：order-service 中的 OrdersServiceImpl 使用 @DubboReference private ICouponService couponService
 * <p>
 * - OpenFeign 调用：使用 @FeignClient 注入 CouponFeignClient 接口（HTTP 协议，通用性强）
 *   示例位置：本接口 CouponFeignClient
 * <p>
 * 【关键注解说明】
 * - @FeignClient(name = "coupon-service")：
 *   - name：目标服务名称（必须与 coupon-service 的 spring.application.name 一致）
 *   - OpenFeign 会从 Nacos 注册中心查找该服务的实例列表
 *   - 负载均衡策略：在 OrderServiceApplication 启动类上通过 @LoadBalancerClient 配置
 *     示例：@LoadBalancerClient(name = "coupon-service", configuration = CouponServiceLoadBalancerConfig.class)
 *     当前使用随机策略（Random），对比 Dubbo 的 @DubboReference(loadbalance = "random")
 * <p>
 * 【使用场景】
 * 1. 调用外部第三方 HTTP 服务（如支付网关、短信服务）
 * 2. 跨语言服务调用（如调用 Python、Node.js 服务）
 * 3. 需要更灵活的 HTTP 请求控制（如自定义 Header、Query 参数）
 * <p>
 * 【注意事项】
 * 1. 目标服务必须注册到 Nacos（配置 spring.cloud.nacos.discovery）
 * 2. 调用方服务必须启用 @EnableFeignClients（在启动类上添加）
 * 3. 接口方法上的 @GetMapping/@PostMapping 必须与目标 Controller 一致
 * 4. 负载均衡配置在调用方启动类上，不在 Feign Client 接口上（因为 service-api 不能依赖 order-service）
 */
@FeignClient(name = "coupon-service", configuration = FeignHeadersLogLevel.class)
public interface CouponFeignClient {

    /**
     * 根据优惠券 ID 查询优惠券信息（HTTP GET 调用示例）
     * <p>
     * 【对应 Dubbo 示例】
     * - Dubbo 接口：ICouponService.getById(Long couponId)（继承自 IService）
     * - Dubbo 调用：OrdersServiceImpl 中使用 @DubboReference 注入后调用
     * <p>
     * 【HTTP 接口对应】
     * - 目标 Controller：CouponController.getById(Long couponId)
     * - 请求路径：GET /coupon-service/coupon/{id}
     * - 返回类型：Result<Coupon>
     * <p>
     * 【@PathVariable 说明】
     * - 用于绑定 URL 路径中的变量
     * - value = "id"：对应 URL 中的 {id} 占位符
     * - 方法参数 couponId 会替换 URL 中的 {id}
     *
     * @param couponId 优惠券 ID
     * @return 优惠券信息
     */
    @GetMapping("/coupon/{id}")
    Result<Coupon> getById(@PathVariable("id") Long couponId);

    /**
     * 扣减优惠券（HTTP POST 调用示例）
     * <p>
     * 【对应 Dubbo 示例】
     * - Dubbo 接口：ICouponService.reduceCoupon(Coupon coupon)
     * - Dubbo 调用：OrdersServiceImpl 中使用 @DubboReference 注入后调用
     * <p>
     * 【HTTP 接口对应】
     * - 目标 Controller：CouponController.reduceCoupon(Coupon coupon)
     * - 请求路径：POST /coupon-service/coupon/reduce
     * - 返回类型：Result<?>
     * <p>
     * 【@RequestBody 说明】
     * - 用于将请求体中的 JSON 数据绑定到方法参数
     * - Coupon 对象会被序列化为 JSON 发送到服务端
     *
     * @param coupon 优惠券信息
     * @return 操作结果
     */
    @PostMapping("/coupon/reduce")
    Result<?> reduceCoupon(@RequestBody Coupon coupon);
}
