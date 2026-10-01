package com.lzj.railway.user.service.registration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.user.common.errorcode.UserErrorCode;
import com.lzj.railway.user.dao.entity.UserDeletionDO;
import com.lzj.railway.user.dao.mapper.UserDeletionMapper;
import com.lzj.railway.user.dto.request.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 注册责任链第三步：限制同一实名证件反复注销并重新注册。 */
@Component
@RequiredArgsConstructor
public class RegisterDeletionLimitHandler implements ChainHandler<RegisterRequest> {

    private static final long MAX_DELETION_COUNT = 5L;

    private final UserDeletionMapper userDeletionMapper;

    /**
     * 按证件类型和证件号统计有效注销记录，达到上限后拒绝注册。
     *
     * @param context 注册上下文
     * @return 未达到限制时继续执行责任链
     */
    @Override
    public ChainDecision handle(RegisterRequest request) {
        Long count = userDeletionMapper.selectCount(new LambdaQueryWrapper<UserDeletionDO>()
                .eq(UserDeletionDO::getIdType, request.getIdType())
                .eq(UserDeletionDO::getIdCard, request.getIdCard())
                .eq(UserDeletionDO::getDelFlag, 0));
        if (count != null && count >= MAX_DELETION_COUNT) {
            throw new ClientException(UserErrorCode.IDENTITY_DELETION_LIMIT_EXCEEDED);
        }
        return ChainDecision.CONTINUE;
    }

    /** 注销次数校验在参数和用户名可用性校验之后执行。 */
    @Override
    public int order() {
        return 200;
    }

}
