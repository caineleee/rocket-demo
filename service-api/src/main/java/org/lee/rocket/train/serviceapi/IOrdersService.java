package org.lee.rocket.train.serviceapi;

import com.baomidou.mybatisplus.extension.service.IService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Order;

/**
 * <p>
 * 订单表 服务类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
public interface IOrdersService extends IService<Order> {

    /**
     * 确认订单
     *
     * @param order 订单信息
     * @return 订单信息
     */
    Result confirmOrder(Order order);
}
