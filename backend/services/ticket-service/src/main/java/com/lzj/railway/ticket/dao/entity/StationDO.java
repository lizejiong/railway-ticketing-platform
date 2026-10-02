package com.lzj.railway.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 车站基础资料。
 *
 * <p>查询侧只使用站点编码、展示名称及所属大区名称来构建 Redis 映射。</p>
 */
@Data
@TableName("t_station")
public class StationDO {

    /** 车站主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 车站编码，例如 VNP。 */
    private String code;

    /** 车站展示名称，例如北京南。 */
    private String name;

    /** 车站所属区域编码。 */
    private String region;

    /** 车站所属区域展示名称，例如北京。 */
    private String regionName;

    /** 逻辑删除标记。 */
    private Integer delFlag;
}
