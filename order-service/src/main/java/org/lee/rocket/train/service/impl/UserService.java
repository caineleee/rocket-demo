package org.lee.rocket.train.service.impl;

import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.dubbo.order.IUserService;

/**
 * @ClassName UserService
 * @Description 实现公共 UserService接口
 * @Author lihongliang
 * @Date 2026/6/2 16:50
 * @Version 1.0
 */
@DubboService(interfaceClass = IUserService.class)
public class UserService implements IUserService {

    @Override
    public String sayHello(String name) {
        return "Hello " + name;
    }
}
