-- 退款幂等键：同一订单明细只能成功退款一次。
-- 已初始化的本地数据库需要手动执行该文件一次；新建 Docker 数据卷会在导入基础表后自动执行。
ALTER TABLE `12306_pay_0`.`t_refund`
    ADD COLUMN `order_item_id` BIGINT NULL COMMENT '订单明细 ID' AFTER `order_sn`,
    ADD UNIQUE KEY `uk_refund_order_item_id` (`order_item_id`);
