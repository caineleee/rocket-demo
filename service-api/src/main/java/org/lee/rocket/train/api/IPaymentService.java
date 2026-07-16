package org.lee.rocket.train.api;

import com.baomidou.mybatisplus.extension.service.IService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Payment;

/**
 * <p>
 * 订单支付表 服务类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
public interface IPaymentService extends IService<Payment> {

    /**
     * 创建支付订单
     * @param payment 支付订单
     */
    Result<?> createPayment(Payment payment);

    /**
     * 支付回调
     * @param payment 支付订单
     * @return 支付回调结果
     */
    Result<?> callbackPayment(Payment payment);
}
