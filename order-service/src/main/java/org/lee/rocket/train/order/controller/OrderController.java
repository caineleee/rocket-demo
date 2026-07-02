package org.lee.rocket.train.order.controller;

import jakarta.annotation.Resource;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Order;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PutMapping("/confirm")
    public Result confirmOrder(@RequestBody Order order) {
        return orderService.confirmOrder(order);
    }

}
