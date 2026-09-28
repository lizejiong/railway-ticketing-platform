package com.lzj.railway.framework.starter.persistence.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.lzj.railway.framework.idgenerator.autoconfigure.IdGeneratorAutoConfiguration;
import com.lzj.railway.framework.idgenerator.snowflake.SnowflakeIdGenerator;
import com.lzj.railway.framework.idgenerator.snowflake.WorkerNodeAssigner;
import com.lzj.railway.framework.starter.persistence.handler.PersistenceMetaObjectHandler;
import com.lzj.railway.framework.starter.persistence.id.MybatisPlusSnowflakeIdentifierGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 通用持久层自动配置。
 */
@AutoConfiguration(after = IdGeneratorAutoConfiguration.class)
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class PersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public PersistenceMetaObjectHandler persistenceMetaObjectHandler() {
        return new PersistenceMetaObjectHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(WorkerNodeAssigner.class)
    static class IdentifierGeneratorConfiguration {

        @Bean
        @ConditionalOnMissingBean(IdentifierGenerator.class)
        MybatisPlusSnowflakeIdentifierGenerator mybatisPlusSnowflakeIdentifierGenerator(
                WorkerNodeAssigner workerNodeAssigner) {
            SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator(workerNodeAssigner);
            return new MybatisPlusSnowflakeIdentifierGenerator(snowflakeIdGenerator);
        }
    }
}
