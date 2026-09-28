# 持久层组件实施计划

> 目标：新增一个 MyBatis-Plus 持久层 Starter，统一分页、基础持久化字段、自动填充、分页模型转换和雪花 ID 接入。

## 设计边界

- 新模块名为 `railway-persistence-spring-boot-starter`，目录为 `backend/components/persistence`。
- 分页对外继续使用 `railway-convention` 中的 `PageRequest`、`PageResponse`，不让业务接口直接暴露 MyBatis-Plus 类型。
- 雪花算法复用 `railway-id-generator`，本模块只实现 MyBatis-Plus 的 `IdentifierGenerator` 适配，避免存在两份算法。
- 默认配置均使用 `@ConditionalOnMissingBean`，业务服务声明同类型 Bean 时可覆盖。
- Redis 节点分配仍由 ID 组件负责；业务服务存在 `WorkerNodeAssigner` 时才替换 MyBatis-Plus 默认 ID 生成器。

## 任务一：注册模块与依赖

**文件：**
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`
- 新建：`backend/components/persistence/pom.xml`

1. 将 `persistence` 加入组件聚合模块。
2. 在统一依赖管理中管理 MyBatis-Plus Starter 和持久层 Starter 版本。
3. 持久层模块直接依赖 `railway-convention`、`railway-common`、`railway-id-generator` 以及 MyBatis-Plus。
4. 加入 Spring Boot 测试依赖。

## 任务二：实现基础持久化对象与元数据填充

**文件：**
- 新建：`backend/components/persistence/src/main/java/com/lzj/railway/framework/starter/persistence/base/BaseDO.java`
- 新建：`backend/components/persistence/src/main/java/com/lzj/railway/framework/starter/persistence/handler/PersistenceMetaObjectHandler.java`
- 测试：`backend/components/persistence/src/test/java/com/lzj/railway/framework/starter/persistence/handler/PersistenceMetaObjectHandlerTest.java`

1. `BaseDO` 定义 `createTime`、`updateTime`、`deleted`。
2. 使用 `@TableField(fill = ...)` 标明新增/修改填充时机，使用 `@TableLogic(value = "0", delval = "1")` 标明逻辑删除语义。
3. 元数据处理器在新增时填充创建时间、修改时间、未删除状态，在修改时刷新修改时间。
4. 测试空字段能填充，已有字段不会被覆盖，修改时只更新应更新的字段。

## 任务三：实现统一 MySQL 分页配置

**文件：**
- 新建：`backend/components/persistence/src/main/java/com/lzj/railway/framework/starter/persistence/config/PersistenceAutoConfiguration.java`
- 新建：`backend/components/persistence/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 测试：`backend/components/persistence/src/test/java/com/lzj/railway/framework/starter/persistence/config/PersistenceAutoConfigurationTest.java`

1. 自动创建 `MybatisPlusInterceptor`。
2. 注册 `PaginationInnerInterceptor(DbType.MYSQL)`。
3. 注册默认元数据处理器。
4. 使用 Spring Boot 3 自动配置导入文件注册配置类。
5. 验证默认 Bean 存在且业务自定义 Bean 可覆盖。

## 任务四：实现分页模型转换工具

**文件：**
- 新建：`backend/components/persistence/src/main/java/com/lzj/railway/framework/starter/persistence/toolkit/PageUtil.java`
- 测试：`backend/components/persistence/src/test/java/com/lzj/railway/framework/starter/persistence/toolkit/PageUtilTest.java`

1. 将规约 `PageRequest` 转为 MyBatis-Plus `Page<T>`。
2. 将 `IPage<T>` 转为规约 `PageResponse<T>`。
3. 提供带 `Function` 的转换重载，一次完成 DO 到 DTO 的记录映射并保留分页信息。
4. 覆盖空记录、普通记录和类型转换测试。

## 任务五：接入统一雪花 ID

**文件：**
- 新建：`backend/components/persistence/src/main/java/com/lzj/railway/framework/starter/persistence/id/MybatisPlusSnowflakeIdentifierGenerator.java`
- 修改：`backend/components/persistence/src/main/java/com/lzj/railway/framework/starter/persistence/config/PersistenceAutoConfiguration.java`
- 测试：`backend/components/persistence/src/test/java/com/lzj/railway/framework/starter/persistence/id/MybatisPlusSnowflakeIdentifierGeneratorTest.java`

1. 实现 MyBatis-Plus `IdentifierGenerator`，内部委托现有 `SnowflakeIdGenerator`。
2. 仅当容器存在 `WorkerNodeAssigner` 且不存在业务自定义 `IdentifierGenerator` 时注册。
3. 将持久层自动配置排序在 ID 组件自动配置之后，使 Redis 节点分配策略可被发现。
4. 测试生成正数 ID、连续 ID 不重复以及条件装配行为。

## 任务六：文档与整体验证

**文件：**
- 修改：`README.md`
- 修改：`backend/README.md`

1. 补充模块目录和用途。
2. 说明实体继承、分页转换、`@TableId(type = IdType.ASSIGN_ID)` 与 Redis/固定节点策略的使用方式。
3. 执行持久层模块测试。
4. 执行后端全量测试并检查 Git 差异。
