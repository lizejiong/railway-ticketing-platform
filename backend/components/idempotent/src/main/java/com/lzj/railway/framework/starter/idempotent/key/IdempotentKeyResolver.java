package com.lzj.railway.framework.starter.idempotent.key;

import com.alibaba.fastjson2.JSON;
import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 按 TOKEN、PARAM 或 SPEL 规则生成稳定且彼此隔离的幂等 Key。
 */
public class IdempotentKeyResolver {

    private final String keyPrefix;
    private final IdempotentTokenProvider tokenProvider;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public IdempotentKeyResolver(String keyPrefix) {
        this(keyPrefix, null);
    }

    public IdempotentKeyResolver(String keyPrefix, IdempotentTokenProvider tokenProvider) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("幂等 Key 前缀不能为空");
        }
        String normalizedPrefix = trimColons(keyPrefix);
        if (normalizedPrefix.isBlank()) {
            throw new IllegalArgumentException("幂等 Key 前缀不能为空");
        }
        this.keyPrefix = normalizedPrefix;
        this.tokenProvider = tokenProvider;
    }

    public String resolve(Method method, Object[] args, Idempotent idempotent) {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(idempotent, "idempotent must not be null");
        Object[] invocationArgs = args == null ? new Object[0] : args;

        String businessKey = switch (idempotent.type()) {
            case TOKEN -> tokenKey(idempotent);
            case PARAM -> digestArguments(invocationArgs);
            case SPEL -> spelKey(method, invocationArgs, idempotent.key());
        };

        List<String> segments = new ArrayList<>();
        segments.add(keyPrefix);
        segments.add(idempotent.scene().name().toLowerCase(Locale.ROOT));
        String uniqueKeyPrefix = trimColons(idempotent.uniqueKeyPrefix());
        segments.add(uniqueKeyPrefix.isBlank() ? methodIdentity(method) : uniqueKeyPrefix);
        segments.add(businessKey);
        return String.join(":", segments);
    }

    private String tokenKey(Idempotent idempotent) {
        if (idempotent.scene() != IdempotentScene.REST_API) {
            throw new IllegalArgumentException("TOKEN 幂等模式只支持 REST_API 场景");
        }
        if (tokenProvider == null) {
            throw new IllegalStateException("当前应用没有可用的 IdempotentTokenProvider");
        }
        String token = tokenProvider.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("幂等 Token 不能为空");
        }
        return token.trim();
    }

    private String spelKey(Method method, Object[] args, String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("SPEL 幂等模式必须配置 @Idempotent.key");
        }
        return evaluateExpression(method, args, expression);
    }

    private String evaluateExpression(Method method, Object[] args, String expression) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("args", args);
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        for (int index = 0; index < args.length; index++) {
            context.setVariable("p" + index, args[index]);
            context.setVariable("a" + index, args[index]);
            if (parameterNames != null && index < parameterNames.length) {
                context.setVariable(parameterNames[index], args[index]);
            }
        }

        try {
            Object value = expressionParser.parseExpression(expression).getValue(context);
            String result = value == null ? null : String.valueOf(value);
            if (result == null || result.isBlank()) {
                throw new IllegalArgumentException("幂等 Key 表达式结果不能为空: " + expression);
            }
            return result.trim();
        } catch (EvaluationException exception) {
            throw new IllegalArgumentException("无法解析幂等 Key 表达式: " + expression, exception);
        }
    }

    private String digestArguments(Object[] args) {
        try {
            return digest(JSON.toJSONBytes(args));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "方法参数无法生成幂等 Key，请改用 TOKEN 或 SPEL 模式",
                    exception
            );
        }
    }

    private String methodIdentity(Method method) {
        String parameters = Stream.of(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(","));
        String identity = method.getDeclaringClass().getName()
                + "#" + method.getName() + "(" + parameters + ")";
        return digest(identity.getBytes(StandardCharsets.UTF_8));
    }

    private String digest(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private String trimColons(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("^:+|:+$", "");
    }
}
