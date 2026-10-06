-- 车票与订单建立精确关联，支付、关闭与退款消息据此更新对应车票状态。
-- 已初始化的本地数据库需要手动执行该文件一次；新建 Docker 数据卷会在导入基础表后自动执行。
ALTER TABLE `12306_ticket`.`t_ticket`
    ADD COLUMN `order_sn` VARCHAR(64) NULL COMMENT '订单号' AFTER `username`,
    ADD KEY `idx_ticket_order_sn` (`order_sn`);
