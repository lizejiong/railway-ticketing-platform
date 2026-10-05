# 车站查询实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 逐项执行。
**目标：** 为余票查询提供公开的车站编码、名称与区域列表。

**架构：** 复用 `StationRegionCache` 已维护的 Redis 站点名称、区域双 Hash；缓存为空时沿用既有分布式锁回源逻辑，控制器不直接访问数据库。

### 任务 1：扩展站点缓存读取能力

**文件：**
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/StationRegionCache.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketStationResponse.java`

- [ ] 增加返回全部站点的缓存读取方法；空缓存时调用既有 `warmUp` 逻辑。
- [ ] 仅输出编码、名称、区域名称，并按编码稳定排序。

### 任务 2：公开车站查询接口并提交

**文件：**
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/controller/StationController.java`

- [ ] 新增 `GET /api/ticket/stations`，不加 JWT 鉴权。
- [ ] 编译 `ticket-service`，通过后提交 `feat: add station query endpoint`。
