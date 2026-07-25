package org.lee.rocket.train.config;

import org.springframework.context.annotation.Bean;

import feign.Logger;

public class FeignFullLogLevel {

    @Bean
    public Logger.Level feignClient() {
        return Logger.Level.FULL;
    }
}
