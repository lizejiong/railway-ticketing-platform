-- 先检查请求区间的每种席别是否充足，再对全部途经区间同步扣减。
local seatTypeCounts = cjson.decode(ARGV[1])
local affectedSegments = cjson.decode(ARGV[2])
local requestedSegment = ARGV[3]

for _, seatTypeCount in ipairs(seatTypeCounts) do
    local field = requestedSegment .. "_" .. tostring(seatTypeCount.seatType)
    local remaining = tonumber(redis.call("hget", KEYS[1], field) or "0")
    if remaining < tonumber(seatTypeCount.count) then
        return 0
    end
end

for _, seatTypeCount in ipairs(seatTypeCounts) do
    for _, routeSegment in ipairs(affectedSegments) do
        local field = tostring(routeSegment.departure) .. "_" .. tostring(routeSegment.arrival)
                .. "_" .. tostring(seatTypeCount.seatType)
        redis.call("hincrby", KEYS[1], field, -tonumber(seatTypeCount.count))
    end
end

return 1
