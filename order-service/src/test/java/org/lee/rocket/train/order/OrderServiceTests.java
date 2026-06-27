package org.lee.rocket.train.order;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.lee.rocket.train.service.entity.Order;
import org.lee.rocket.train.serviceapi.IOrdersService;
import org.springframework.boot.test.context.SpringBootTest;


/**
 * @ClassName OrderServiceTests
 * @Description
 * @Author lihongliang
 * @Date 2026/6/26 10:55
 * @Version 1.0
 */
@SpringBootTest
public class OrderServiceTests {

    @Resource
    private IOrdersService ordersService;

    @Test
    public void ConfirmOrder() {

        Order order = new Order();
        order.setGoodsId(2001L);
        order.setUserId(1002L);
        order.setCouponId(3002L);
        order.setAddress("北京");
        order.setGoodsNumber(1);
        order.setGoodsPrice(7999_00L);
        order.setOrderAmount(7999_00L);
        order.setShippingFee(0L);
        order.setMoneyPaid(100_00L);
        ordersService.confirmOrder(order);
    }
}
