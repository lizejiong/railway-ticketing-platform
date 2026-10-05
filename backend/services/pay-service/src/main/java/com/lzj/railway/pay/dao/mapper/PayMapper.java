package com.lzj.railway.pay.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzj.railway.pay.dao.entity.PayDO;
import org.apache.ibatis.annotations.Mapper;

/** 支付单持久层。 */
@Mapper
public interface PayMapper extends BaseMapper<PayDO> {
}
