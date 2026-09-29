package com.lzj.railway.framework.starter.idempotent.annotation;

import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 标记需要通过 Redis 状态记录控制重复执行的业务方法。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 使用场景，决定重复请求是抛出异常还是直接跳过。 */
    IdempotentScene scene() default IdempotentScene.REST_API;

    /** 判断重复调用时使用的 Key 生成方式。 */
    IdempotentType type() default IdempotentType.PARAM;

    /**
     * 业务唯一标识的 SpEL 表达式，仅在 {@link IdempotentType#SPEL} 下使用。
     */
    String key() default "";

    /** 当前业务操作的 Redis Key 前缀，用于隔离不同业务。 */
    String uniqueKeyPrefix() default "";

    /** 幂等状态保留时长；成功后从完成时刻重新计时，并应大于业务方法最长执行时间。 */
    long keyTimeout() default 3600L;

    /** 幂等标记保留时长的单位。 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** REST API 重复提交时返回的提示。 */
    String message() default "请勿重复提交";
}
