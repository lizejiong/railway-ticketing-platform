package com.lzj.railway.user.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.user.dto.request.PassengerCreateRequest;
import com.lzj.railway.user.dto.request.PassengerUpdateRequest;
import com.lzj.railway.user.dto.response.PassengerResponse;
import com.lzj.railway.user.service.PassengerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前登录用户的乘车人管理接口。 */
@RestController
@RequestMapping("/api/user/passengers")
@RequiredArgsConstructor
@Tag(name = "乘车人管理", description = "当前登录用户的乘车人增删改查")
@SecurityRequirement(name = "bearerAuth")
public class PassengerController {

    private final PassengerService passengerService;

    /** 查询当前用户的乘车人列表。 */
    @GetMapping
    @Operation(summary = "查询乘车人列表")
    public Result<List<PassengerResponse>> list() {
        return Results.success(passengerService.listCurrentUserPassengers());
    }

    /** 新增当前用户的乘车人。 */
    @PostMapping
    @Operation(summary = "新增乘车人")
    public Result<PassengerResponse> create(@Valid @RequestBody PassengerCreateRequest request) {
        return Results.success(passengerService.create(request));
    }

    /** 修改当前用户拥有的乘车人。 */
    @PutMapping("/{passengerId}")
    @Operation(summary = "修改乘车人")
    public Result<PassengerResponse> update(@PathVariable Long passengerId,
                                            @Valid @RequestBody PassengerUpdateRequest request) {
        return Results.success(passengerService.update(passengerId, request));
    }

    /** 软删除当前用户拥有的乘车人。 */
    @DeleteMapping("/{passengerId}")
    @Operation(summary = "删除乘车人")
    public Result<Void> delete(@PathVariable Long passengerId) {
        passengerService.delete(passengerId);
        return Results.success();
    }
}
