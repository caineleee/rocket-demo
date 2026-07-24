package org.lee.rocket.train.order.controller;

import jakarta.annotation.Resource;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.api.feign.GoodsFeignClient;
import org.lee.rocket.train.common.model.Result;
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
     * <p>
     * 通过 @Resource 注入 GoodsFeignClient，用于通过 HTTP 调用 goods-service。
     * <p>
     * 【与 Dubbo 的对比】
     * - Dubbo 注入方式：@DubboReference private IGoodsService goodsService
     *   示例位置：OrdersServiceImpl 中的 Dubbo 调用
     * - Feign 注入方式：@Resource private GoodsFeignClient goodsFeignClient
     *   示例位置：本字段
     */
    @Resource
    private GoodsFeignClient goodsFeignClient;

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
     * <p>
     * 【与 Dubbo 调用的对比】
     * - Dubbo 调用：OrdersServiceImpl 中的 goodsService.findById(goodsId)
     *   使用 @DubboReference 注入，RPC 协议，高性能
     * - Feign 调用：本方法中的 goodsFeignClient.findById(goodsId)
     *   使用 @Resource 注入 Feign Client，HTTP 协议，通用性强
     * <p>
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
}
