package org.lee.rocket.train.payment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lee.rocket.train.api.IMqMessageProducerService;
import org.lee.rocket.train.service.entity.MqMessageProducer;
import org.lee.rocket.train.service.mapper.MqMessageProducerMapper;
import org.springframework.stereotype.Service;

/**
 * <p>
 * MQ 消息生产表 服务实现类
 * </p>
 *
 * <p>本实现作为 pay-service 的本地 Spring Bean 存在（{@code @Service}），
 * 供 {@code PaymentServiceImpl} 与 {@code PaymentMqSendCompensateTask} 通过
 * {@link jakarta.annotation.Resource @Resource} 注入使用，承担支付回调 MQ 消息的
 * 持久化、状态更新与删除等本地数据操作。</p>
 *
 * @author CodeGenerator
 * @since 2026-07-02
 */
@Service
public class MqMessageProducerServiceImpl
        extends ServiceImpl<MqMessageProducerMapper, MqMessageProducer>
        implements IMqMessageProducerService {
}
