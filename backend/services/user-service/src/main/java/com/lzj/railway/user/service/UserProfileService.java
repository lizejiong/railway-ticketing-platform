package com.lzj.railway.user.service;

import com.lzj.railway.user.dto.response.UserProfileResponse;

/** 当前登录用户资料服务。 */
public interface UserProfileService {

    /** 查询当前登录用户的脱敏资料。 */
    UserProfileResponse getCurrentProfile();
}
