package org.lee.rocket.train.order.controller;

import jakarta.annotation.Resource;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.feign.CouponFeignClient;
import org.lee.rocket.train.feign.GoodsFeignClient;
import org.lee.rocket.train.service.entity.Coupon;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.service.entity.Order;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 订单表 前端控制器
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource // OrderServiceImpl 实际在服务内, 通JVM 直接用 Resource 注解即可.
    private IOrdersService orderService;

    /**
     * 【OpenFeign 示例】注入 Feign Client
     * 通过 @Resource 注入 GoodsFeignClient，用于通过 HTTP 调用 goods-service。
     */
    @Resource
    private GoodsFeignClient goodsFeignClient;

    /**
     * 【OpenFeign 示例】注入 Feign Client
     * 通过 @Resource 注入 CouponFeignClient，用于通过 HTTP 调用 coupon-service。
     */
    @Resource
    private CouponFeignClient couponFeignClient;

    @PutMapping("/confirm")
    public Result<?> confirmOrder(@RequestBody Order order) {
        return orderService.confirmOrder(order);
    }

    /**
     * 【OpenFeign 示例】通过 HTTP 调用 goods-service 查询商品信息
     * <p>
     * 这是一个测试接口，演示如何通过 OpenFeign 调用其他服务。
     * <p>
     * 【调用链路】
     * 1. 客户端请求：GET /order/feign/goods/{id}
     * 2. OrderController.findById() 接收请求
     * 3. 通过 GoodsFeignClient 发起 HTTP GET 请求
     * 4. goods-service 的 GoodsController.findById() 处理请求
     * 5. 返回商品信息
     * 【测试方法】
     * curl http://localhost:8081/order/feign/goods/1
     *
     * @param goodsId 商品 ID
     * @return 商品信息
     */
    @GetMapping("/feign/goods/{id}")
    public Result<Goods> findGoodsByIdFeign(@PathVariable("id") Long goodsId) {
        // 通过 OpenFeign HTTP 调用 goods-service
        // 对比 Dubbo 调用：goodsService.findById(goodsId)
        return goodsFeignClient.findById(goodsId);
    }

    /**
     * 【OpenFeign 示例】通过 HTTP 调用 coupon-service 查询优惠券信息
     * <p>
     * 这是一个测试接口，演示如何通过 OpenFeign 调用 coupon-service。
     * <p>
     * 【调用链路】
     * 1. 客户端请求：GET /order/feign/coupon/{id}
     * 2. OrderController.findCouponByIdFeign() 接收请求
     * 3. 通过 CouponFeignClient 发起 HTTP GET 请求
     * 4. coupon-service 的 CouponController.getById() 处理请求
     * 5. 返回优惠券信息
     * 【测试方法】
     * curl http://localhost:8081/order/feign/coupon/1
     *
     * @param couponId 优惠券 ID
     * @return 优惠券信息
     */
    @GetMapping("/feign/coupon/{id}")
    public Result<Coupon> findCouponByIdFeign(@PathVariable("id") Long couponId) {
        // 通过 OpenFeign HTTP 调用 coupon-service
        // 对比 Dubbo 调用：couponService.getById(couponId)
        return couponFeignClient.getById(couponId);
    }
}
