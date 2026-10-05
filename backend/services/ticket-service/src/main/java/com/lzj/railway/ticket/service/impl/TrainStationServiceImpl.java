package com.lzj.railway.ticket.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dao.entity.TrainStationDO;
import com.lzj.railway.ticket.dao.mapper.TrainStationMapper;
import com.lzj.railway.ticket.dto.response.TrainStationResponse;
import com.lzj.railway.ticket.service.TrainStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** 基于列车站序表还原行程详情。 */
@Service
@RequiredArgsConstructor
public class TrainStationServiceImpl implements TrainStationService {
    private final TrainStationMapper trainStationMapper;

    /**
     * 站序在存储层是字符串，读取时转换为数字排序，避免 "10" 排在 "2" 前面。
     */
    @Override
    public List<TrainStationResponse> listTrainStations(Long trainId) {
        if (trainId == null || trainId <= 0) {
            throw new ClientException(TicketErrorCode.PURCHASE_PARAMETER_INVALID);
        }
        return trainStationMapper.selectList(Wrappers.<TrainStationDO>lambdaQuery()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .eq(TrainStationDO::getDelFlag, 0))
                .stream()
                .sorted(Comparator.comparing(this::sequenceNumber))
                .map(item -> new TrainStationResponse(sequenceNumber(item), item.getDeparture(), item.getArrival(),
                        item.getArrivalTime(), item.getDepartureTime(), item.getStopoverTime()))
                .toList();
    }

    /** 将异常站序排在末尾，保证脏数据不会导致整个公开查询失败。 */
    private int sequenceNumber(TrainStationDO station) {
        try {
            return Integer.parseInt(station.getSequence());
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }
}
