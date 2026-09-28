package com.lzj.railway.framework.idgenerator.autoconfigure;

import com.lzj.railway.framework.idgenerator.snowflake.FixedWorkerNodeAssigner;
import com.lzj.railway.framework.idgenerator.snowflake.RedisWorkerNodeAssigner;
import com.lzj.railway.framework.idgenerator.snowflake.WorkerNodeAssigner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdGeneratorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdGeneratorAutoConfiguration.class));

    @Test
    void shouldCreateRedisAssignerWhenRedisTemplateExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        contextRunner
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisWorkerNodeAssigner.class);
                    assertThat(context).hasSingleBean(WorkerNodeAssigner.class);
                });
    }

    @Test
    void shouldStayDisabledWithoutRedisTemplate() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(WorkerNodeAssigner.class));
    }

    @Test
    void shouldBackOffForCustomWorkerNodeAssigner() {
        WorkerNodeAssigner customAssigner = new FixedWorkerNodeAssigner(0);

        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(WorkerNodeAssigner.class, () -> customAssigner)
                .run(context -> assertThat(context.getBean(WorkerNodeAssigner.class)).isSameAs(customAssigner));
    }
}
