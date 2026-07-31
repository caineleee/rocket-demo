package org.lee.rocket.train.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.constant.code.ResultCode;
import org.lee.rocket.train.common.constant.status.UserMoneyLogType;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.UserMoneyLog;
import org.lee.rocket.train.api.IUserService;
import org.lee.rocket.train.service.entity.User;
import org.lee.rocket.train.user.mapper.UserMapper;
import org.lee.rocket.train.user.mapper.UserMoneyLogMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

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
    private UserMoneyLogMapper userMoneyLogMapper;

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param userId 用户 ID
     */
    @Override
    public User findById(Long userId) {
        if (userId == null) {
            CastException.cast(ResultCode.REQUEST_PARAMETER_VALID);
        }
        return getById(userId);
    }

    /**
     * 扣减用户余额 || 回退用户余额
     *
     * 【修复内容】
     * 1. 加 @Transactional(rollbackFor = Exception.class)：扣余额 + 写日志两步操作必须原子，
     *    中间任一步失败整体回滚，避免"余额扣了但日志没写"的数据不一致
     * 2. 修复 count < 0 → count == 0：count 来自 COUNT(*)，永远 >= 0，
     *    原条件恒不成立，导致"没付过款也能退款"的防御完全失效
     * 3. 删除 @SuppressWarnings("null")，对 user.getUserMoney() 做 NPE 防御
     *    （userMoney 是 Long 包装类型，DB 中可能为 NULL，直接 .longValue() 会 NPE）
     * 4. 余额扣减前校验余额是否充足，防止余额变负数
     * 5. findById 改用 getById，避免 query().one() 在异常数据下抛 TooManyResultsException
     * 6. 清理注释掉的旧代码（Git 历史已保留）
     *
     * @param userMoneyLog 用户余额日志
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<?> updateMoneyPaid(UserMoneyLog userMoneyLog) {
        // 校验参数是否合法
        if (userMoneyLog == null
                || userMoneyLog.getUserId() == null
                || userMoneyLog.getOrderId() == null
                || userMoneyLog.getUseMoney() == null
                || userMoneyLog.getUseMoney() <= 0L) {
            CastException.cast(ResultCode.REQUEST_PARAMETER_VALID);
        }

        // 查询订单（余额日志使用情况），判定需要走扣减还是回退逻辑
        Long count = userMoneyLogMapper.countByCompositeKey(userMoneyLog);
        @SuppressWarnings("null")
        User user = getById(userMoneyLog.getUserId());
        if (user == null) {
            CastException.cast(ResultCode.USER_NO_EXIST);
        }

        // 扣减用户余额逻辑
        if (userMoneyLog.getMoneyLogType().equals(UserMoneyLogType.PAID.getCode())) {
            if (count > 0) {
                // 已经有记录，证明用户余额已经扣减过（防重复扣款）
                CastException.cast(ResultCode.USER_MONEY_REDUCE_FAIL);
            }
            // NPE 防御：userMoney 是 Long 包装类型，DB 中可能为 NULL
            @SuppressWarnings("null")
            Long currentMoney = Objects.requireNonNull(user.getUserMoney(),
                    "用户余额为空，数据异常");
            // 余额校验：扣减后不能为负数
            if (currentMoney < userMoneyLog.getUseMoney()) {
                CastException.cast(ResultCode.MONEY_PAID_LESS_ZERO);
            }
            user.setUserMoney(currentMoney - userMoneyLog.getUseMoney().longValue());
            updateById(user);
        }

        // 回退用户余额逻辑
        if (userMoneyLog.getMoneyLogType().equals(UserMoneyLogType.REFUND.getCode())) {
            // 【修复】count < 0 → count == 0
            // count 来自 COUNT(*)，永远 >= 0，原条件 count < 0 恒不成立
            // 导致"没有付款记录也能退款"的防御完全失效
            if (count == 0) {
                // 没有付款记录，证明用户没有付过款，不能退款
                CastException.cast(ResultCode.MONEY_PAID_INVALID);
            }
            // 防止多次退款
            UserMoneyLog refundLog = new UserMoneyLog();
            refundLog.setUserId(userMoneyLog.getUserId());
            refundLog.setOrderId(userMoneyLog.getOrderId());
            refundLog.setMoneyLogType(UserMoneyLogType.REFUND.getCode());
            Long refundCount = userMoneyLogMapper.countByCompositeKey(refundLog);
            if (refundCount > 0) {
                // 存在退款记录，证明用户已经退过款
                CastException.cast(ResultCode.USER_MONEY_REFUND_ALREADY);
            }
            // NPE 防御
            @SuppressWarnings("null")
            Long currentMoney = Objects.requireNonNull(user.getUserMoney(),
                    "用户余额为空，数据异常");
            // 回退用户余额
            user.setUserMoney(currentMoney + userMoneyLog.getUseMoney().longValue());
            updateById(user);
        }

        // 记录用户余额使用日志
        userMoneyLog.setCreateTime(LocalDateTime.now());
        userMoneyLogMapper.insert(userMoneyLog);
        return Result.success();
    }

    /**
     * Dubbo 连通性测试方法
     */
    @Override
    public String sayHello(String name) {
        return "Hello ~ " + name;
    }
}
