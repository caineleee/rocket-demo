package org.lee.rocket.train.payment;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
    "org.lee.rocket.train.payment",
    "org.lee.rocket.train.common"
})
// 同时扫描支付模块自身 Mapper 与 service-pojo 共享 Mapper（如 MqMessageProducerMapper），
// 与 goods-service 的 @MapperScan 配置保持一致
@MapperScan({"org.lee.rocket.train.payment.mapper", "org.lee.rocket.train.service.mapper"})
@EnableDubbo
@EnableScheduling
public class PayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayServiceApplication.class, args);
	}

}
