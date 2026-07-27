package org.lee.rocket.train.coupon.controller;

import jakarta.annotation.Resource;
import org.lee.rocket.train.api.ICouponService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Coupon;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 优惠券表 前端控制器
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@RestController
@RequestMapping("/coupon")
public class CouponController {

    @Resource
    private ICouponService couponService;

    /**
     * 根据优惠券 ID 查询优惠券信息（HTTP GET 接口）
     * <p>
     * 【OpenFeign 示例】
     * 这是一个 REST API 接口，供其他服务通过 OpenFeign HTTP 调用。
     * <p>
     * 【与 Dubbo 的对比】
     * - Dubbo 接口：ICouponService.getById(Long couponId)（继承自 IService）
     *   实现：由 MyBatis Plus 提供
     *   调用方式：@DubboReference 注入（RPC 协议）
     * <p>
     * - HTTP 接口：本方法 getById(Long couponId)
     *   调用方式：@FeignClient 注入（HTTP 协议）
     *   示例：order-service 中的 CouponFeignClient.getById()
     * <p>
     * 【使用场景】
     * 1. 跨语言服务调用（如 Python、Node.js 服务调用 Java 服务）
     * 2. 调用外部第三方 HTTP 服务
     * 3. 需要更灵活的 HTTP 请求控制（如自定义 Header、Query 参数）
     * <p>
     * 【注意事项】
     * 1. 返回值必须使用 Result<T> 包装，与 Feign Client 接口定义一致
     * 2. 请求路径必须与 Feign Client 的 @GetMapping 路径一致
     * 3. 参数绑定使用 @PathVariable，与 Feign Client 的 @PathVariable 对应
     *
     * @param couponId 优惠券 ID
     * @return 优惠券信息
     */
    @GetMapping("/{id}")
    public Result<Coupon> getById(@PathVariable("id") Long couponId) {
        Coupon coupon = couponService.getById(couponId);
        if (coupon != null) {
            return Result.success(coupon);
        }
        return Result.error("优惠券不存在");
    }

    /**
     * 扣减优惠券（HTTP POST 接口）
     * <p>
     * 【OpenFeign 示例】
     * 这是一个 REST API 接口，供其他服务通过 OpenFeign HTTP 调用。
     * <p>
     * 【与 Dubbo 的对比】
     * - Dubbo 接口：ICouponService.reduceCoupon(Coupon coupon)
     *   实现：CouponServiceImpl.reduceCoupon(Coupon coupon)
     *   调用方式：@DubboReference 注入（RPC 协议）
     * <p>
     * - HTTP 接口：本方法 reduceCoupon(Coupon coupon)
     *   调用方式：@FeignClient 注入（HTTP 协议）
     *   示例：order-service 中的 CouponFeignClient.reduceCoupon()
     * <p>
     * 【注意事项】
     * 1. 使用 @RequestBody 接收 JSON 格式的请求体
     * 2. 返回值必须使用 Result<T> 包装，与 Feign Client 接口定义一致
     * 3. 请求路径必须与 Feign Client 的 @PostMapping 路径一致
     *
     * @param coupon 优惠券信息
     * @return 操作结果
     */
    @PostMapping("/reduce")
    public Result<?> reduceCoupon(@RequestBody Coupon coupon) {
        return couponService.reduceCoupon(coupon);
    }
}
