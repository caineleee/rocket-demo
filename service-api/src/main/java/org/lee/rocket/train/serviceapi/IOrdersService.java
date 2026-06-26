package org.lee.rocket.train.serviceapi;

import com.baomidou.mybatisplus.extension.service.IService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Orders;

/**
 * <p>
 * 订单表 服务类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
public interface IOrdersService extends IService<Orders> {

    /**
     * 确认订单
     *
     * @param orders 订单信息
     * @return 订单信息
     */
    Result confirmOrder(Orders orders);
}
