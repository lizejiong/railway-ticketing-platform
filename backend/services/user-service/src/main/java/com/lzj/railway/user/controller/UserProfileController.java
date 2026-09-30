package com.lzj.railway.user.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.user.dto.response.UserProfileResponse;
import com.lzj.railway.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户资料接口。 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户资料", description = "当前登录用户资料查询")
public class UserProfileController {

    private final UserProfileService userProfileService;

    /** 查询当前登录用户的脱敏资料。 */
    @GetMapping("/profile")
    @Operation(summary = "查询当前用户资料")
    @SecurityRequirement(name = "bearerAuth")
    public Result<UserProfileResponse> getCurrentProfile() {
        return Results.success(userProfileService.getCurrentProfile());
    }
}
