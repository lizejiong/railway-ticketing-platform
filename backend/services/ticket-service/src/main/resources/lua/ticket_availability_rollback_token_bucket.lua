-- 取消或后续锁座失败时，将此前预扣的全部途经区间令牌一次性回补。
local seatTypeCounts = cjson.decode(ARGV[1])
local affectedSegments = cjson.decode(ARGV[2])

for _, seatTypeCount in ipairs(seatTypeCounts) do
    for _, routeSegment in ipairs(affectedSegments) do
        local field = tostring(routeSegment.departure) .. "_" .. tostring(routeSegment.arrival)
                .. "_" .. tostring(seatTypeCount.seatType)
        redis.call("hincrby", KEYS[1], field, tonumber(seatTypeCount.count))
    end
end

return 1
