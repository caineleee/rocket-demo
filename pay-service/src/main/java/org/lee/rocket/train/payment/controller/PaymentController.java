package org.lee.rocket.train.payment.controller;

import jakarta.annotation.Resource;
import org.lee.rocket.train.api.IPaymentService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Payment;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 订单支付表 前端控制器
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@RestController
// 【修复】原 @RequestMapping("/payment") 与 Gateway 路由 Path=/pay/** 不匹配，
// 导致经网关访问 /pay/create、/pay/callback 全部 404（此前仅靠直连 8083 端口 /payment/* 测试未暴露）。
// 按项目约定"Controller 路径简化成 /服务名/..."，统一为 /pay。
@RequestMapping("/pay")
public class PaymentController {

    @Resource
    private IPaymentService paymentService;

    /**
     * 创建支付订单
     * @param payment 支付订单
     * @return 结果
     */
    @PutMapping("/create")
    public Result<?> createPayment(@RequestBody Payment payment) {
        return paymentService.createPayment(payment);
    }

    /**
     * 支付回调
     * @param payment 支付订单
     * @return 结果
     */
    @PutMapping("/callback")
    public Result<?> callbackPayment(@RequestBody Payment payment) {
        return paymentService.callbackPayment(payment);
    }
}
