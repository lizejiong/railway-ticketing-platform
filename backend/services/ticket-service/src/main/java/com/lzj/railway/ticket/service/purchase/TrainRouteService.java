package com.lzj.railway.ticket.service.purchase;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dao.entity.TrainStationDO;
import com.lzj.railway.ticket.dao.mapper.TrainStationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 根据列车站序计算购票所覆盖的连续区间。
 */
@Service
@RequiredArgsConstructor
public class TrainRouteService {

    private final TrainStationMapper trainStationMapper;

    /**
     * 查询并计算指定行程覆盖的相邻区间。
     *
     * <p>例如列车站序为 A→B→C，购买 A→C 时返回 A→B、B→C。
     * 锁座与令牌桶均以这组最小区间作为操作单位。</p>
     *
     * @param trainId 列车主键
     * @param departure 出发站名称
     * @param arrival 到达站名称
     * @return 连续且有序的最小区间列表
     */
    public List<TrainRouteSegment> listRouteSegments(Long trainId, String departure, String arrival) {
        List<TrainStationDO> stations = listTrainStations(trainId);
        return resolveRouteSegments(stations, departure, arrival);
    }

    /**
     * 查询列车所有可售区间，用于初始化该列车的完整余票令牌桶。
     *
     * <p>站序 A→B→C 会生成 A→B、A→C、B→C。前两者分别服务于相邻区间扣减和
     * 长行程余票校验，二者必须同时存在于同一个令牌桶中。</p>
     *
     * @param trainId 列车主键
     * @return 已按站序组合的全部可售区间
     */
    public List<TrainRouteSegment> listAllSaleSegments(Long trainId) {
        List<String> stationNames = listStationNames(trainId);
        List<TrainRouteSegment> result = new ArrayList<>();
        for (int departureIndex = 0; departureIndex < stationNames.size(); departureIndex++) {
            for (int arrivalIndex = departureIndex + 1; arrivalIndex < stationNames.size(); arrivalIndex++) {
                result.add(new TrainRouteSegment(
                        stationNames.get(departureIndex), stationNames.get(arrivalIndex)));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 计算一次购票会影响余票的全部可售区间。
     *
     * <p>列车站序为 A→B→C→D 时，购买 B→C 不仅要影响 B→C，
     * 还会影响 A→C、A→D、B→D 等与该行程重叠的售票区间；
     * 这样任意查询区间都能立即看到一致的余票变化。</p>
     *
     * @param trainId 列车主键
     * @param departure 出发站名称
     * @param arrival 到达站名称
     * @return 需要同步扣减或回补的全部可售区间
     */
    public List<TrainRouteSegment> listAffectedSaleSegments(
            Long trainId, String departure, String arrival) {
        List<String> stations = listStationNames(trainId);
        int departureIndex = stations.indexOf(departure);
        int arrivalIndex = stations.indexOf(arrival);
        if (departureIndex < 0 || arrivalIndex < 0 || departureIndex >= arrivalIndex) {
            throw new ClientException(TicketErrorCode.JOURNEY_INVALID);
        }
        List<TrainRouteSegment> result = new ArrayList<>();
        if (departureIndex != 0) {
            for (int index = 0; index < departureIndex; index++) {
                for (int endIndex = 1; endIndex < stations.size() - departureIndex; endIndex++) {
                    result.add(new TrainRouteSegment(stations.get(index), stations.get(departureIndex + endIndex)));
                }
            }
        }
        for (int index = departureIndex; index <= arrivalIndex; index++) {
            for (int endIndex = index + 1; endIndex < stations.size() && index < arrivalIndex; endIndex++) {
                result.add(new TrainRouteSegment(stations.get(index), stations.get(endIndex)));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 从已排序的站序记录中解析连续区间。
     *
     * <p>该方法不访问数据库，便于单元测试覆盖站序断裂和反向行程等边界。</p>
     *
     * @param stations 已按 sequence 升序排列的站序记录
     * @param departure 出发站名称
     * @param arrival 到达站名称
     * @return 连续区间列表
     */
    static List<TrainRouteSegment> resolveRouteSegments(
            List<TrainStationDO> stations, String departure, String arrival) {
        List<TrainRouteSegment> result = new ArrayList<>();
        boolean collecting = false;
        String expectedDeparture = departure;
        for (TrainStationDO station : stations) {
            if (!collecting) {
                if (!Objects.equals(station.getDeparture(), departure)) {
                    continue;
                }
                collecting = true;
            }
            if (!Objects.equals(station.getDeparture(), expectedDeparture)
                    || station.getArrival() == null) {
                break;
            }
            result.add(new TrainRouteSegment(station.getDeparture(), station.getArrival()));
            if (Objects.equals(station.getArrival(), arrival)) {
                return List.copyOf(result);
            }
            expectedDeparture = station.getArrival();
        }
        throw new ClientException(TicketErrorCode.JOURNEY_INVALID);
    }

    /**
     * 按站序读取列车站点数据。
     *
     * @param trainId 列车主键
     * @return 有序站序记录
     */
    private List<TrainStationDO> listTrainStations(Long trainId) {
        return trainStationMapper.selectList(
                Wrappers.<TrainStationDO>lambdaQuery()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .eq(TrainStationDO::getDelFlag, 0)
                        .orderByAsc(TrainStationDO::getSequence));
    }

    /**
     * 将站序记录转换为不重复且有序的站点名称列表。
     *
     * @param trainId 列车主键
     * @return 列车完整站序
     */
    private List<String> listStationNames(Long trainId) {
        List<String> stationNames = new ArrayList<>();
        for (TrainStationDO station : listTrainStations(trainId)) {
            addStationName(stationNames, station.getDeparture());
            addStationName(stationNames, station.getArrival());
        }
        return stationNames;
    }

    /**
     * 仅在站名不为空且不同于上一个站点时加入站序。
     *
     * @param stationNames 已收集站序
     * @param stationName 待加入站名
     */
    private void addStationName(List<String> stationNames, String stationName) {
        if (stationName != null && (stationNames.isEmpty()
                || !Objects.equals(stationNames.get(stationNames.size() - 1), stationName))) {
            stationNames.add(stationName);
        }
    }
}
