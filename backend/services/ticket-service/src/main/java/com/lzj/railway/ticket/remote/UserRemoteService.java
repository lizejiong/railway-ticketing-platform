package com.lzj.railway.ticket.remote;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.ticket.remote.dto.PassengerActualRemoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 用户域乘车人快照的内部调用契约。 */
@FeignClient(name = "user-service")
public interface UserRemoteService {
    /** 按用户名分片键和主键集合批量读取乘车人原始快照。 */
    @GetMapping("/api/user/inner/passengers/query")
    Result<List<PassengerActualRemoteResponse>> listPassengers(@RequestParam("username") String username,
                                                                @RequestParam("ids") List<Long> ids);
}
