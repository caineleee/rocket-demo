package org.lee.rocket.train.common.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 简化版 IdWorker 配置
 * 只需要注册一个 Bean，其他地方直接 @Autowired 使用即可
 */
@Configuration
public class IdWorkerConfig {

    @Value("${id-worker.worker-id:1}")
    private long workerId;

    @Value("${id-worker.datacenter-id:1}")
    private long dataCenterId;

    @Bean
    public DefaultIdentifierGenerator idWorker() {
        return new DefaultIdentifierGenerator(workerId, dataCenterId);
    }
}