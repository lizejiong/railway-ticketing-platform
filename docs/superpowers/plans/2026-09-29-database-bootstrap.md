# 数据库初始化入口实施计划

> **给 agentic workers：** 必须使用 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。
**目标：** 增加一份可重复执行的 MySQL 建库脚本，为现有用户、订单、支付和票务 SQL 提供导入前置条件。

**架构：** 初始化资产位于 `deploy/`。脚本只创建数据库，不复制或修改用户提供的建表、数据 SQL；所有库采用既有 DDL 的 `utf8mb4` / `utf8mb4_unicode_ci`。

**技术栈：** MySQL 8.x、SQL DDL、PowerShell 静态校验。

---

### 任务 1：增加幂等的建库脚本

**文件：**
- 新建：`deploy/mysql/init/00-create-databases.sql`

- [x] **步骤 1：编写建库 SQL**

```sql
CREATE DATABASE IF NOT EXISTS `12306_user_0`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `12306_user_1`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `12306_order_0`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `12306_order_1`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `12306_pay_0`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `12306_pay_1`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `12306_ticket`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- [x] **步骤 2：静态验证建库范围和幂等性**

```powershell
$script = Get-Content -Raw deploy/mysql/init/00-create-databases.sql
$names = [regex]::Matches($script, 'CREATE DATABASE IF NOT EXISTS `([^`]+)`') | ForEach-Object { $_.Groups[1].Value }
$names.Count
$names | Sort-Object
```

预期：输出 `7`，名称为 `12306_order_0`、`12306_order_1`、`12306_pay_0`、`12306_pay_1`、`12306_ticket`、`12306_user_0`、`12306_user_1`。

- [x] **步骤 3：检查 SQL 格式**

```powershell
git diff --check -- deploy/mysql/init/00-create-databases.sql
```

预期：无输出，退出码为 `0`。

- [ ] **步骤 4：提交变更**

```bash
git add deploy/mysql/init/00-create-databases.sql
git commit -m "feat: add database bootstrap script"
```

### 任务 2：验证与现有 SQL 的导入顺序

**文件：**
- 验证：`deploy/mysql/init/00-create-databases.sql`
- 外部输入：`D:/code/12306/resources/db/12306-springcloud-{user,order,pay,ticket}.sql`
- 外部输入：`D:/code/12306/resources/data/12306-springcloud-{ticket,user}.sql`

- [x] **步骤 1：确认脚本覆盖所有 `USE` 的库**

```powershell
$ddlFiles = Get-ChildItem D:/code/12306/resources/db/*.sql
$used = foreach ($file in $ddlFiles) { [regex]::Matches((Get-Content -Raw $file), '(?im)^\s*USE\s+`?([^`;\s]+)') | ForEach-Object { $_.Groups[1].Value } }
$created = [regex]::Matches((Get-Content -Raw deploy/mysql/init/00-create-databases.sql), 'CREATE DATABASE IF NOT EXISTS `([^`]+)`') | ForEach-Object { $_.Groups[1].Value }
Compare-Object ($used | Sort-Object -Unique) ($created | Sort-Object -Unique)
```

预期：无输出。

- [x] **步骤 2：按以下顺序导入**

```text
1. 执行 deploy/mysql/init/00-create-databases.sql。
2. 依次导入 resources/db 下的 user、order、pay、ticket 建表脚本。
3. 导入 resources/data/12306-springcloud-ticket.sql。
4. 导入 resources/data/12306-springcloud-user.sql。
```

- [x] **步骤 3：最终工作区检查**

```powershell
git status --short
git diff --check
```

预期：仅出现本计划明确新增的建库脚本与计划文件；`git diff --check` 无输出。

### 任务 3：使用 Docker Compose 固化本地 MySQL 环境

**文件：**
- 新建：`deploy/compose.yaml`
- 新建：`deploy/.env.example`

**执行调整：** 外部的四份建表 SQL 将 `USE` 和数据库名拆成两行，MySQL 8 无法直接执行。保留原文件不变，改为以 `.source` 后缀挂载，并由 `deploy/mysql/init/90-import-provided-sql.sh` 在容器内将其规范化为带反引号的单行 `USE` 语句后再导入。

- [x] **步骤 1：添加可配置的本地环境变量模板**

```dotenv
SQL_ASSET_ROOT=D:/code/12306/resources
MYSQL_ROOT_PASSWORD=12306-root
MYSQL_PORT=3306
```

- [x] **步骤 2：添加 MySQL Compose 服务**

```yaml
services:
  mysql:
    image: mysql:8.0.36
    container_name: 12306-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?Set MYSQL_ROOT_PASSWORD in deploy/.env}
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    ports:
      - "${MYSQL_PORT:-3306}:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - type: bind
        source: ./mysql/init/00-create-databases.sql
        target: /docker-entrypoint-initdb.d/00-create-databases.sql
        read_only: true
      - type: bind
        source: ${SQL_ASSET_ROOT:?Set SQL_ASSET_ROOT in deploy/.env}/db/12306-springcloud-user.sql
        target: /docker-entrypoint-initdb.d/10-user-schema.sql
        read_only: true
      - type: bind
        source: ${SQL_ASSET_ROOT:?Set SQL_ASSET_ROOT in deploy/.env}/db/12306-springcloud-order.sql
        target: /docker-entrypoint-initdb.d/20-order-schema.sql
        read_only: true
      - type: bind
        source: ${SQL_ASSET_ROOT:?Set SQL_ASSET_ROOT in deploy/.env}/db/12306-springcloud-pay.sql
        target: /docker-entrypoint-initdb.d/30-pay-schema.sql
        read_only: true
      - type: bind
        source: ${SQL_ASSET_ROOT:?Set SQL_ASSET_ROOT in deploy/.env}/db/12306-springcloud-ticket.sql
        target: /docker-entrypoint-initdb.d/40-ticket-schema.sql
        read_only: true
      - type: bind
        source: ${SQL_ASSET_ROOT:?Set SQL_ASSET_ROOT in deploy/.env}/data/12306-springcloud-ticket.sql
        target: /docker-entrypoint-initdb.d/50-ticket-data.sql
        read_only: true
      - type: bind
        source: ${SQL_ASSET_ROOT:?Set SQL_ASSET_ROOT in deploy/.env}/data/12306-springcloud-user.sql
        target: /docker-entrypoint-initdb.d/60-user-data.sql
        read_only: true
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p\"$$MYSQL_ROOT_PASSWORD\" --silent"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 20s

volumes:
  mysql-data:
```

- [x] **步骤 3：生成本地配置并静态校验**

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/compose.yaml config
```

预期：配置展开成功，`mysql` 服务包含 7 个只读初始化 SQL 挂载和命名卷 `mysql-data`。

- [x] **步骤 4：启动并验证实际建库结果**

```powershell
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d
docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T mysql mysql -uroot -p12306-root -N -e "SHOW DATABASES LIKE '12306\\_%';"
docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T mysql mysql -uroot -p12306-root -N -e "SELECT table_schema, COUNT(*) FROM information_schema.tables WHERE table_schema LIKE '12306\\_%' GROUP BY table_schema ORDER BY table_schema;"
```

结果：显示 7 个 `12306_*` 数据库；表数量分别为 `user_0=66`、`user_1=64`、`order_0=48`、`order_1=48`、`pay_0=17`、`pay_1=16`、`ticket=9`。

- [x] **步骤 5：记录初始化重置方式**

```powershell
docker compose --env-file deploy/.env -f deploy/compose.yaml down -v
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d
```

预期：删除 `mysql-data` 后，下次启动重新执行全部初始化 SQL；普通的 `up -d` 不会重放 SQL。
