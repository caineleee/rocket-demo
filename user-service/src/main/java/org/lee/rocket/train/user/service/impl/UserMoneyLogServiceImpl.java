package org.lee.rocket.train.user.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lee.rocket.train.api.IUserMoneyLogService;
import org.lee.rocket.train.service.entity.UserMoneyLog;
import org.lee.rocket.train.user.mapper.UserMoneyLogMapper;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户余额变动日志表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-05
 */
@Service  // 当前只需要模块内调用, 不需要暴露给其他模块调用, 定义为本地 bean
public class UserMoneyLogServiceImpl extends ServiceImpl<UserMoneyLogMapper, UserMoneyLog> implements IUserMoneyLogService {

}
