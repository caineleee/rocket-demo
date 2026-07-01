package org.lee.rocket.train.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.lee.rocket.train.service.entity.MqConsumerLog;

/**
 * <p>
 * mq 消息消费日志表 Mapper 接口
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-29
 */
@Mapper
public interface MqConsumerLogMapper extends BaseMapper<MqConsumerLog> {

    /**
     * 根据复合主键查询
     * @param groupName 消费者组名
     * @param msgTag    消息标签
     * @param msgKey    消息Key
     */
    @Select("SELECT * FROM tb_mq_consumer_log " +
            "WHERE group_name = #{groupName} AND msg_tag = #{msgTag} AND msg_key = #{msgKey}")
    @ResultMap("BaseResultMap") // 引用XML中定义的resultMap
    MqConsumerLog selectByCompositeKey(@Param("groupName") String groupName,
                                       @Param("msgTag") String msgTag,
                                       @Param("msgKey") String msgKey);

}
