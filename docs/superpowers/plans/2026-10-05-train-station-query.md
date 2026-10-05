# 列车经停站查询实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 逐项执行。
**目标：** 提供余票列表后的列车行程详情查询。

**架构：** 由票务服务直接查询 `t_train_station`，按字符串站序转换为数值排序；站点数据是低频读取，不新增缓存层。

### 任务 1：新增经停站 DTO、服务与公开接口

**文件：**
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dto/response/TrainStationResponse.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/TrainStationService.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/impl/TrainStationServiceImpl.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/controller/TrainStationController.java`

- [ ] 校验 trainId 为正数，查询未删除记录并按站序升序返回。
- [ ] 公开 `GET /api/ticket/trains/{trainId}/stations`。
- [ ] 编译票务服务并提交 `feat: add train station query`。
