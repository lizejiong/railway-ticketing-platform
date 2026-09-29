# Nacos 本地开发基础设施实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 按任务逐项执行本计划；步骤使用 `- [ ]` 跟踪状态。

**目标：** 在现有 Docker Compose 中提供启用鉴权、可持久化的单机 Nacos 2.x 开发环境，供后续 Spring Cloud Alibaba 服务注册与配置管理使用。

**架构：** 新增独立的 `nacos` Compose 服务，固定到 `nacos/nacos-server:v2.5.4`，使用 Nacos 内嵌 Derby 存储并把数据和日志放入命名卷。它不依赖现有 MySQL，也不建立 Nacos 数据库；生产环境的 Nacos 集群、外部 MySQL 与高可用配置另行设计。映射 8848 HTTP/控制台端口与 9848 gRPC 客户端端口，启用 Nacos 鉴权并把凭据仅放在 `.env` 中。

**技术栈：** Docker Compose、Nacos 2.5.4、Nacos 内嵌 Derby、Spring Cloud Alibaba Nacos 客户端。

---

### 任务 1：定义 Nacos Compose 服务

**文件：**
- 修改：`deploy/compose.yaml`

- [x] **步骤 1：添加 Nacos 服务与命名卷**

在 `services:` 下增加：

```yaml
  nacos:
    image: nacos/nacos-server:v2.5.4
    container_name: 12306-nacos
    restart: unless-stopped
    environment:
      MODE: standalone
      PREFER_HOST_MODE: hostname
      NACOS_AUTH_ENABLE: "true"
      NACOS_AUTH_TOKEN: ${NACOS_AUTH_TOKEN:?Set NACOS_AUTH_TOKEN in deploy/.env}
      NACOS_AUTH_IDENTITY_KEY: ${NACOS_AUTH_IDENTITY_KEY:?Set NACOS_AUTH_IDENTITY_KEY in deploy/.env}
      NACOS_AUTH_IDENTITY_VALUE: ${NACOS_AUTH_IDENTITY_VALUE:?Set NACOS_AUTH_IDENTITY_VALUE in deploy/.env}
      JVM_XMS: 256m
      JVM_XMX: 256m
    ports:
      - "${NACOS_PORT:-8848}:8848"
      - "${NACOS_GRPC_PORT:-9848}:9848"
    volumes:
      - nacos-data:/home/nacos/data
      - nacos-logs:/home/nacos/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://127.0.0.1:8848/nacos/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s
```

并在顶层 `volumes:` 中增加：

```yaml
  nacos-data:
  nacos-logs:
```

- [x] **步骤 2：验证 Compose 模型**

Run: `docker compose --env-file deploy/.env -f deploy/compose.yaml config --quiet`

Expected: 退出码为 0。

### 任务 2：提供开发环境变量示例与操作说明

**文件：**
- 修改：`deploy/.env.example`
- 修改：`README.md`

- [x] **步骤 1：添加本地 Nacos 环境变量**

在 `deploy/.env.example` 增加：

```dotenv
NACOS_PORT=8848
NACOS_GRPC_PORT=9848
NACOS_AUTH_TOKEN=VGhpc0lzTG9jYWxEZXZlbG9wbWVudE5hY29zVG9rZW5Gb3IxMjMwNg==
NACOS_AUTH_IDENTITY_KEY=local-nacos-server
NACOS_AUTH_IDENTITY_VALUE=12306-local-development
```

示例 token 只允许用于本地开发；注释必须明确生产环境应生成不同的 Base64 密钥，且其原文长度不得少于 32 字节。

- [x] **步骤 2：记录启动与验证方式**

在 README 的部署/本地开发部分增加：

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d nacos
docker compose --env-file deploy/.env -f deploy/compose.yaml ps nacos
```

说明控制台地址为 `http://localhost:8848/nacos`，本地开发默认账号为 `nacos` / `nacos`，后续服务需要同时使用 HTTP 地址 `localhost:8848` 和 gRPC 端口 `9848`。明确 Nacos 以单机 Derby 模式运行，仅限本地开发。

### 任务 3：运行验证

**文件：**
- 不新增文件。

- [x] **步骤 1：校验 Compose 渲染结果**

Run: `docker compose --env-file deploy/.env -f deploy/compose.yaml config --quiet`

Expected: 退出码为 0。

- [x] **步骤 2：启动单机 Nacos 并等待健康状态**

Run: `docker compose --env-file deploy/.env -f deploy/compose.yaml up -d nacos`

Run: `docker compose --env-file deploy/.env -f deploy/compose.yaml ps nacos`

Expected: `nacos` 状态为 `running (healthy)`。

- [x] **步骤 3：检查 HTTP 健康端点**

Run: `Invoke-WebRequest http://localhost:8848/nacos/actuator/health -UseBasicParsing`

Expected: HTTP 200，响应表示 Nacos 健康。

- [x] **步骤 4：检查改动格式**

Run: `git diff --check`

Expected: 无输出且退出码为 0。
