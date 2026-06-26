package org.lee.rocket.train.payment.controller;

import org.apache.dubbo.config.annotation.DubboReference;
import org.lee.rocket.train.serviceapi.IUserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ClassName UserController
 * @Description
 * @Author lihongliang
 * @Date 2026/6/2 18:08
 * @Version 1.0
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @DubboReference(version = "1.0.0", group = "default")
    private IUserService userService;

    @RequestMapping("/hello")
    public String hello(String name) {
        return userService.sayHello(name);
    }
}
