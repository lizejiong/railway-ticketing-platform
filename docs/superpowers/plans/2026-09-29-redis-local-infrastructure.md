# Redis 本地开发基础设施实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 按任务逐项执行，并用 `- [ ]` 跟踪状态。

**目标：** 为当前 Spring Cloud Alibaba 项目新增可认证、AOF 持久化、可健康检查的 Redis 本地开发容器。

**架构：** 在现有 `deploy/compose.yaml` 中新增独立 `redis` 服务，不依赖 MySQL 或 Nacos；通过密码保护本机映射的 6379 端口，并将数据保存到命名卷。缓存、分布式 ID 和幂等组件已经具备 Redis 依赖，本阶段只提供中间件环境，不为任何业务服务写 Redis 配置。

**技术栈：** Docker Compose、Redis 7.4.2 Alpine、AOF 持久化、Redis CLI。

---

### 任务 1：定义 Redis Compose 服务

**文件：**
- 修改：`deploy/compose.yaml`

- [x] **步骤 1：添加 Redis 服务**

在 `services:` 下增加：

```yaml
  redis:
    image: redis:7.4.2-alpine
    container_name: 12306-redis
    restart: unless-stopped
    command:
      - redis-server
      - --appendonly
      - "yes"
      - --requirepass
      - ${REDIS_PASSWORD:?Set REDIS_PASSWORD in deploy/.env}
    ports:
      - "${REDIS_PORT:-6379}:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli --no-auth-warning -a \"$$REDIS_PASSWORD\" ping | grep PONG"]
      interval: 5s
      timeout: 3s
      retries: 12
      start_period: 10s
```

同时为容器注入 `REDIS_PASSWORD: ${REDIS_PASSWORD:?Set REDIS_PASSWORD in deploy/.env}`，并在顶层 `volumes:` 增加 `redis-data:`。

- [x] **步骤 2：校验 Compose 模型**

Run: `docker compose --env-file deploy/.env -f deploy/compose.yaml config --quiet`

Expected: 退出码为 0。

### 任务 2：补充环境变量与本地文档

**文件：**
- 修改：`deploy/.env.example`
- 修改：`README.md`
- 修改：`deploy/.env`（本地文件，已被 Git 忽略）

- [x] **步骤 1：定义 Redis 本地变量**

在 `.env.example` 与本地 `.env` 增加：

```dotenv
REDIS_PORT=6379
REDIS_PASSWORD=12306-redis
```

在 `.env.example` 中说明该密码只用于本地开发，生产环境需换成独立强密码或秘密管理服务。

- [x] **步骤 2：记录启动、检查与停止命令**

在 README 的 Nacos 章节之后增加：

```powershell
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d redis
docker compose --env-file deploy/.env -f deploy/compose.yaml ps redis
docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T redis sh -c 'redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping'
```

说明 Redis 地址为 `localhost:6379`、已启用认证、使用 AOF 和 `redis-data` 命名卷，并且服务应用连接时应从环境变量或 Nacos 获取密码。

### 任务 3：启动并验证 Redis

**文件：**
- 不新增文件。

- [x] **步骤 1：启动 Redis**

Run: `docker compose --env-file deploy/.env -f deploy/compose.yaml up -d redis`

Expected: 容器创建并启动。

- [x] **步骤 2：等待健康状态并验证认证**

Run: `docker compose --env-file deploy/.env -f deploy/compose.yaml ps redis`

Expected: 状态为 `running (healthy)`。

Run: `docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T redis sh -c 'redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping'`

Expected: 输出 `PONG`。

- [x] **步骤 3：检查变更格式**

Run: `git diff --check`

Expected: 无输出且退出码为 0。
