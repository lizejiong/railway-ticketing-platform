# 支付域 Nacos 配置

创建以下 Data ID，Group 均为 `DEFAULT_GROUP`。本地从 IDEA 启动的服务直接连接宿主机 RocketMQ，因此 NameServer 默认值为 `127.0.0.1:9876`。

## `pay-service.yaml`

```yaml
server:
  port: 8084

spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:12306-redis}
      database: ${REDIS_DATABASE:0}
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}

rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
  producer:
    group: pay-service-producer

railway:
  user:
    jwt:
      secret: ${USER_JWT_SECRET:local-development-user-service-jwt-secret-change-me}
      issuer: railway-platform

pay:
  mock:
    # 仅本地开发联调打开；生产环境保持 false。
    enabled: ${PAY_MOCK_ENABLED:false}
  alipay:
    app-id: ${PAY_ALIPAY_APP_ID:}
    private-key: ${PAY_ALIPAY_PRIVATE_KEY:}
    alipay-public-key: ${PAY_ALIPAY_PUBLIC_KEY:}
    server-url: ${PAY_ALIPAY_SERVER_URL:https://openapi-sandbox.dl.alipaydev.com/gateway.do}
    notify-url: ${PAY_ALIPAY_NOTIFY_URL:}
    format: json
    charset: UTF-8
    sign-type: RSA2
```

## 向 `order-service.yaml` 和 `ticket-service.yaml` 增加的内容

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
```

## 向 `gateway-service.yaml` 增加的路由

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: pay-service
          uri: lb://pay-service
          predicates:
            - Path=/api/pay/**
          filters:
            - name: TokenValidate
              args:
                publicPaths:
                  - /api/pay/callback/alipay
```

`/api/pay/callback/alipay` 必须由网关匿名放行，否则支付宝无法通知；安全边界由支付服务内的支付宝 RSA2 验签、订单金额校验和状态条件更新承担。其余 `/api/pay/**` 路径必须携带用户 JWT。

> 不要把支付宝应用私钥、支付宝公钥或公网回调地址提交到 Git。回调地址必须能从支付宝沙箱公网访问，通常需要使用受控的反向代理或隧道。
