package org.lee.rocket.train.order;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.lee.rocket.train.common.config.IdWorkerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan("org.lee.rocket.train.order.mapper")
@EnableDubbo
@Import(IdWorkerConfig.class)  // 导入 common 模块的 IdWorker 配置
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
