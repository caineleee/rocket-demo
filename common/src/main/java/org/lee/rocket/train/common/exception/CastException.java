package org.lee.rocket.train.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.constant.code.ResultCode;

/**
 * 业务异常抛出工具
 * <p>
 * 注意：此方法总是抛出异常，永远不会正常返回。
 * 返回类型设为 RuntimeException 以便 IDE 能识别出后续代码不可达。
 * <p>
 * 状态码与响应码分离后，统一用 {@link #cast(ResultCode)} 抛业务异常
 * （原基于 ShopCode 的重载已随 ShopCode 一并移除）。
 *
 * @author lihongliang
 */
@Slf4j
public class CastException {

    /**
     * 抛出业务异常（推荐用法，基于 ResultCode 响应码）
     *
     * @param code 响应码
     * @return 永不返回（恒抛异常），设为 RuntimeException 便于 IDE 识别后续不可达
     */
    public static RuntimeException cast(ResultCode code) {
        log.error(code.toString());
        throw new CustomerException(code);
    }
}
