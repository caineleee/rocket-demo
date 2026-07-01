package org.lee.rocket.train.payment.service.impl;

import org.lee.rocket.train.payment.mapper.OrderPaymentMapper;
import org.lee.rocket.train.service.entity.OrderPayment;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lee.rocket.train.api.IOrderPaymentService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单支付表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Service
public class OrderPaymentServiceImpl extends ServiceImpl<OrderPaymentMapper, OrderPayment> implements IOrderPaymentService {

}
