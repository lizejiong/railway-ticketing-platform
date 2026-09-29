package com.lzj.railway.framework.starter.idempotent.enums;

/**
 * 幂等处理场景。
 */
public enum IdempotentScene {

    /** REST API 重复请求，检测到重复时抛出客户端异常。 */
    REST_API,

    /** MQ 重复投递，检测到重复时跳过本次方法调用。 */
    MQ
}
