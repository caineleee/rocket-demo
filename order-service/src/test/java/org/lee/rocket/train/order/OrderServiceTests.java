package org.lee.rocket.train.order;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.lee.rocket.train.service.entity.Orders;
import org.lee.rocket.train.serviceapi.IOrdersService;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

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

        Orders orders = new Orders();
        orders.setGoodsId(2001L);
        orders.setUserId(1002L);
        orders.setCouponId(3002L);
        orders.setAddress("北京");
        orders.setGoodsNumber(1);
        orders.setGoodsPrice(new BigDecimal("7999.00"));
        orders.setOrderAmount(new BigDecimal("7999.00"));
        orders.setShippingFee(BigDecimal.ZERO);
        orders.setMoneyPaid(new BigDecimal("100.00"));
        ordersService.confirmOrder(orders);
    }
}
