package org.lee.rocket.train.payment;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.lee.rocket.train.payment.mapper")
@EnableDubbo
@EnableScheduling
public class PayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayServiceApplication.class, args);
	}

}
