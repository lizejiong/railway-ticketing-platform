package com.lzj.railway.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzj.railway.user.dao.entity.PassengerDO;
import org.apache.ibatis.annotations.Mapper;

/** 乘车人数据访问接口。 */
@Mapper
public interface PassengerMapper extends BaseMapper<PassengerDO> {
}
