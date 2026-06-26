package org.lee.rocket.train.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.UserMoneyLog;
//import org.lee.rocket.train.serviceapi.IUserMoneyLogService;
import org.lee.rocket.train.serviceapi.IUserService;
import org.lee.rocket.train.service.entity.User;
import org.lee.rocket.train.user.mapper.UserMapper;
import org.lee.rocket.train.user.mapper.UserMoneyLogMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@DubboService(interfaceClass = IUserService.class)
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
//    private IUserMoneyLogService userMoneyLogService;
    private UserMoneyLogMapper userMoneyLogMapper;

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param userId 用户 ID
     */
    @Override
    public User findById(Long userId) {
        if (userId == null) {
            CastException.cast(ShopCode.REQUEST_PARAMETER_VALID);
        }
        return query().eq("user_id", userId).one();
    }

    /**
     * 扣减用户余额 || 回退用户余额
     *
     * @param userMoneyLog 用户余额日志
     */
    @Override
    public Result updateMoneyPaid(UserMoneyLog userMoneyLog) {
        // 校验参数是否合法
        if (userMoneyLog == null
                || userMoneyLog.getUserId() == null
                || userMoneyLog.getOrderId() == null
                || userMoneyLog.getUseMoney() == null
                || userMoneyLog.getUseMoney().compareTo(BigDecimal.ZERO) <= 0) {
            CastException.cast(ShopCode.REQUEST_PARAMETER_VALID);
        }
        // 查询订单(余额日志使用情况), 判定需要走扣减还是回退逻辑
//        Long count = userMoneyLogService.lambdaQuery()
//                .eq(UserMoneyLog::getUserId, userMoneyLog.getUserId())
//                .eq(UserMoneyLog::getOrderId, userMoneyLog.getOrderId()).count();
        Long count = userMoneyLogMapper.countByCompositeKey(userMoneyLog);
        User user = query().eq("user_id", userMoneyLog.getUserId()).one();

        if (user == null) {
            CastException.cast(ShopCode.USER_NO_EXIST);
        }

        // 扣减用户余额逻辑
        if (userMoneyLog.getMoneyLogType().equals(ShopCode.USER_MONEY_PAID.getCode())) {
            if (count > 0) {
                // 已经有记录, 证明用户余额已经使用过
                CastException.cast(ShopCode.ORDER_PAY_STATUS_IS_PAY);
            }
            // 扣减用户余额
            user.setUserMoney(user.getUserMoney() - userMoneyLog.getUseMoney().longValue());
            updateById(user);
        }
        // 回退用户余额逻辑
        if (userMoneyLog.getMoneyLogType().equals(ShopCode.USER_MONEY_REFUND.getCode())) {
            if(count < 0) {
                // 没有记录, 证明用户没有付过款
                CastException.cast(ShopCode.ORDER_PAY_STATUS_NO_PAY);
            }
            // 防止多次退款
            UserMoneyLog userMoneyLog1 = new UserMoneyLog();
            userMoneyLog1.setUserId(userMoneyLog.getUserId());
            userMoneyLog1.setOrderId(userMoneyLog.getOrderId());
            userMoneyLog1.setMoneyLogType(ShopCode.USER_MONEY_REFUND.getCode());
//            Long count1 = userMoneyLogService.lambdaQuery(userMoneyLog1).count();
            Long count1 = userMoneyLogMapper.countByCompositeKey(userMoneyLog1);
            if (count1 > 0) {
                // 存在记录, 证明用户已经退过款
                CastException.cast(ShopCode.USER_MONEY_REFUND_ALREADY);
            }
            // 回退用户余额
            user.setUserMoney(user.getUserMoney() + userMoneyLog.getUseMoney().longValue());
            updateById(user);
        }
        // 记录用户余额使用日志
        userMoneyLog.setCreateTime(LocalDateTime.now());
//        userMoneyLogService.save(userMoneyLog);
        userMoneyLogMapper.insert(userMoneyLog);
        // 返回结果
        return Result.success(ShopCode.SUCCESS);
    }

    /**
     * dubbo测试
     * @param name
     * @return
     */
    @Override
    public String sayHello(String name) {
        return "Hello ~ " + name;
    }
}
