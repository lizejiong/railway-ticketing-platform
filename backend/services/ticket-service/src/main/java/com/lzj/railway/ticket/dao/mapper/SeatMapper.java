package com.lzj.railway.ticket.dao.mapper;

import com.lzj.railway.ticket.dao.mapper.dto.SeatRemainingDTO;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 座位余票查询 Mapper。
 */
public interface SeatMapper {

    /**
     * 统计指定车次在给定区间内各席别的未锁定座位数。
     *
     * @param trainIds 列车主键集合
     * @param departure 出发站
     * @param arrival 到达站
     * @return 按列车和席别分组的余票
     */
    List<SeatRemainingDTO> countAvailableSeatsByTrainIds(@Param("trainIds") Collection<Long> trainIds,
                                                         @Param("departure") String departure,
                                                         @Param("arrival") String arrival);
}
