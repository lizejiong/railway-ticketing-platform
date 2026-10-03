package com.lzj.railway.user.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.user.dto.response.PassengerActualResponse;
import com.lzj.railway.user.service.PassengerService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 服务注册网络内供票务域使用的乘车人快照接口。 */
@RestController
@RequestMapping("/api/user/inner/passengers")
@RequiredArgsConstructor
public class InternalPassengerController {
    private final PassengerService passengerService;

    /** 批量读取原始乘车人信息；该路径不应配置为网关公开路由。 */
    @GetMapping("/query")
    @Operation(summary = "内部查询乘车人原始信息", hidden = true)
    public Result<List<PassengerActualResponse>> listActualByIds(@RequestParam String username,
                                                                  @RequestParam List<Long> ids) {
        return Results.success(passengerService.listPassengerActualByIds(username, ids));
    }
}
