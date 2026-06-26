package org.lee.rocket.train.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.lee.rocket.train.service.entity.UserMoneyLog;

import static org.apache.commons.collections.CollectionUtils.select;

/**
 * <p>
 * 用户余额变动日志表 Mapper 接口
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-05
 */
public interface UserMoneyLogMapper extends BaseMapper<UserMoneyLog> {
    /**
     * 根据复合主键查询
     *
     * @param entity UserMoneyLog
     * @return
     */
    default UserMoneyLog selectByCompositeKey(UserMoneyLog entity) {
        return selectOne(new LambdaQueryWrapper<UserMoneyLog>()
                .eq(UserMoneyLog::getUserId, entity.getUserId())
                .eq(UserMoneyLog::getOrderId, entity.getOrderId())
                .eq(UserMoneyLog::getMoneyLogType, entity.getMoneyLogType()));
    }

    /**
     * 根据复合主键查询数量
     *
     * @param entity UserMoneyLog
     * @return
     */
    default Long countByCompositeKey(UserMoneyLog entity) {
        return selectCount(new LambdaQueryWrapper<UserMoneyLog>()
                .eq(UserMoneyLog::getUserId, entity.getUserId())
                .eq(UserMoneyLog::getOrderId, entity.getOrderId())
                .eq(UserMoneyLog::getMoneyLogType, entity.getMoneyLogType()));
    }

    /**
     * 根据复合主键更新
     *
     * @param entity UserMoneyLog
     * @return
     */
    default int updateByCompositeKey(UserMoneyLog entity) {
        return update(entity, new LambdaUpdateWrapper<UserMoneyLog>()
                .eq(UserMoneyLog::getUserId, entity.getUserId())
                .eq(UserMoneyLog::getOrderId, entity.getOrderId())
                .eq(UserMoneyLog::getMoneyLogType, entity.getMoneyLogType()));
    }

    /**
     * 根据复合主键删除
     *
     * @param entity UserMoneyLog
     * @return
     */
    default int deleteByCompositeKey(UserMoneyLog entity) {
        return delete(new LambdaQueryWrapper<UserMoneyLog>()
                .eq(UserMoneyLog::getUserId, entity.getUserId())
                .eq(UserMoneyLog::getOrderId, entity.getOrderId())
                .eq(UserMoneyLog::getMoneyLogType, entity.getMoneyLogType()));
    }
}
