package com.lzj.railway.framework.starter.persistence.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.lzj.railway.framework.idgenerator.snowflake.FixedWorkerNodeAssigner;
import com.lzj.railway.framework.idgenerator.snowflake.WorkerNodeAssigner;
import com.lzj.railway.framework.starter.persistence.handler.PersistenceMetaObjectHandler;
import com.lzj.railway.framework.starter.persistence.id.MybatisPlusSnowflakeIdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PersistenceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PersistenceAutoConfiguration.class));

    @Test
    void shouldConfigureMysqlPaginationAndMetadataHandler() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            assertThat(interceptor.getInterceptors())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(PaginationInnerInterceptor.class);
            assertThat(context).hasSingleBean(PersistenceMetaObjectHandler.class);
            assertThat(context).doesNotHaveBean(IdentifierGenerator.class);
        });
    }

    @Test
    void shouldConfigureSnowflakeGeneratorWhenWorkerNodeAssignerExists() {
        contextRunner
                .withBean(WorkerNodeAssigner.class, () -> new FixedWorkerNodeAssigner(7))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdentifierGenerator.class);
                    assertThat(context.getBean(IdentifierGenerator.class))
                            .isInstanceOf(MybatisPlusSnowflakeIdentifierGenerator.class);
                });
    }

    @Test
    void shouldBackOffWhenApplicationProvidesCustomBeans() {
        MybatisPlusInterceptor customInterceptor = new MybatisPlusInterceptor();
        MetaObjectHandler customHandler = mock(MetaObjectHandler.class);

        contextRunner
                .withBean(MybatisPlusInterceptor.class, () -> customInterceptor)
                .withBean(MetaObjectHandler.class, () -> customHandler)
                .run(context -> {
                    assertThat(context.getBean(MybatisPlusInterceptor.class)).isSameAs(customInterceptor);
                    assertThat(context.getBean(MetaObjectHandler.class)).isSameAs(customHandler);
                    assertThat(context).doesNotHaveBean(PersistenceMetaObjectHandler.class);
                });
    }

    @Test
    void shouldStartTogetherWithMybatisPlusOnSpringBootThree() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MybatisPlusAutoConfiguration.class,
                        PersistenceAutoConfiguration.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("sqlSessionFactory");
                    assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
                });
    }
}
