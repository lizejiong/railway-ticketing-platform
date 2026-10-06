package com.lzj.railway.ticket.service;

import com.lzj.railway.ticket.dto.response.TrainStationResponse;

import java.util.List;

/** 列车经停站与时刻表查询服务。 */
public interface TrainStationService {
    /** 查询指定列车的相邻区间经停信息。 */
    List<TrainStationResponse> listTrainStations(Long trainId);
}
