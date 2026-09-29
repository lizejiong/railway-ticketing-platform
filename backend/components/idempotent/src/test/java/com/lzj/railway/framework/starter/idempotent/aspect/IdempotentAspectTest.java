package com.lzj.railway.framework.starter.idempotent.aspect;

import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentType;
import com.lzj.railway.framework.starter.idempotent.executor.IdempotentExecutor;
import com.lzj.railway.framework.starter.idempotent.key.IdempotentKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentAspectTest {

    @Test
    void shouldResolveKeyAndRouteInvocationToMatchingExecutor() {
        CapturingExecutor executor = new CapturingExecutor();
        IdempotentAspect aspect = new IdempotentAspect(
                List.of(executor),
                new IdempotentKeyResolver("test:idempotent")
        );
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new SampleService());
        proxyFactory.addAspect(aspect);
        SampleService proxy = proxyFactory.getProxy();

        assertThat(proxy.submit("O-300")).isEqualTo("accepted-O-300");
        assertThat(executor.key).isEqualTo("test:idempotent:rest_api:order:submit:O-300");
    }

    private static class CapturingExecutor implements IdempotentExecutor {

        private String key;

        @Override
        public IdempotentScene scene() {
            return IdempotentScene.REST_API;
        }

        @Override
        public Object execute(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key) throws Throwable {
            this.key = key;
            return joinPoint.proceed();
        }
    }

    static class SampleService {

        @Idempotent(
                type = IdempotentType.SPEL,
                key = "#orderId",
                uniqueKeyPrefix = "order:submit"
        )
        public String submit(String orderId) {
            return "accepted-" + orderId;
        }
    }
}
