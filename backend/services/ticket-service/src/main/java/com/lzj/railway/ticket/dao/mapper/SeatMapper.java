package com.lzj.railway.ticket.dao.mapper;

import com.lzj.railway.ticket.dao.mapper.dto.SeatRemainingDTO;
import com.lzj.railway.ticket.dao.entity.SeatDO;
import com.lzj.railway.ticket.service.purchase.TrainRouteSegment;
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

    /**
     * 查询在所有受影响区间均可售的实体座位。
     *
     * @param trainId 列车主键
     * @param seatType 席别编码
     * @param affectedSegments 本次购票影响的全部区间
     * @param limit 所需座位数量
     * @return 可用于锁定的实体座位
     */
    List<SeatDO> selectAvailableSeatsForSegments(
            @Param("trainId") Long trainId,
            @Param("seatType") Integer seatType,
            @Param("affectedSegments") List<TrainRouteSegment> affectedSegments,
            @Param("limit") Integer limit);

    /**
     * 在座位仍可售时锁定一个区间记录。
     *
     * @param trainId 列车主键
     * @param carriageNumber 车厢编号
     * @param seatNumber 座位编号
     * @param departure 区间出发站名称
     * @param arrival 区间到达站名称
     * @return 实际更新的记录数
     */
    int lockSeat(@Param("trainId") Long trainId,
                 @Param("carriageNumber") String carriageNumber,
                 @Param("seatNumber") String seatNumber,
                 @Param("departure") String departure,
                 @Param("arrival") String arrival);

    /**
     * 释放一个已锁定区间记录。
     *
     * @param trainId 列车主键
     * @param carriageNumber 车厢编号
     * @param seatNumber 座位编号
     * @param departure 区间出发站名称
     * @param arrival 区间到达站名称
     * @return 实际更新的记录数
     */
    int unlockSeat(@Param("trainId") Long trainId,
                   @Param("carriageNumber") String carriageNumber,
                   @Param("seatNumber") String seatNumber,
                   @Param("departure") String departure,
                   @Param("arrival") String arrival);
}
