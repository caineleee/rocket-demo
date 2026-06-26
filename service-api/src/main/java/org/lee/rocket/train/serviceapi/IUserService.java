package org.lee.rocket.train.serviceapi;

import com.baomidou.mybatisplus.extension.service.IService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.User;
import org.lee.rocket.train.service.entity.UserMoneyLog;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
public interface IUserService extends IService<User> {

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param userId 用户 ID
     */
    User findById(Long userId);

    /**
     * 扣减用户余额 || 回退用户余额
     *
     * @param userMoneyLog 用户余额变动日志
     */
    Result updateMoneyPaid(UserMoneyLog userMoneyLog);

    /**
     * 测试
     *
     * @param name
     * @return
     */
    String sayHello(String name);
}
