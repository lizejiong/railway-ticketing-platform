package com.lzj.railway.framework.starter.idempotent.enums;

/**
 * 判断两个调用是否属于同一业务操作的 Key 生成方式。
 */
public enum IdempotentType {

    /** 从 HTTP Idempotency-Key 请求头读取客户端生成的幂等 Token。 */
    TOKEN,

    /** 对全部方法参数进行序列化并计算摘要。 */
    PARAM,

    /** 使用注解中的 SpEL 表达式提取业务唯一字段。 */
    SPEL
}
