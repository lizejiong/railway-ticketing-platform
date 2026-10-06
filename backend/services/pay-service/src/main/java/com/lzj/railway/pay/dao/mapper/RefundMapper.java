package com.lzj.railway.pay.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzj.railway.pay.dao.entity.RefundDO;
import org.apache.ibatis.annotations.Mapper;

/** 退款记录持久层。 */
@Mapper
public interface RefundMapper extends BaseMapper<RefundDO> {
}
