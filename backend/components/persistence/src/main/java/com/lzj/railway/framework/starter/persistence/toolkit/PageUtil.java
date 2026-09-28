package com.lzj.railway.framework.starter.persistence.toolkit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzj.railway.framework.convention.page.PageRequest;
import com.lzj.railway.framework.convention.page.PageResponse;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 规约分页对象与 MyBatis-Plus 分页对象之间的转换工具。
 */
public final class PageUtil {

    private PageUtil() {
    }

    /**
     * 将接口层分页参数转换为 MyBatis-Plus 查询分页对象。
     */
    public static <T> Page<T> convert(PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
    }

    /**
     * 将 MyBatis-Plus 分页结果转换为框架无关的分页响应。
     */
    public static <T> PageResponse<T> convert(IPage<T> page) {
        return convert(page, Function.identity());
    }

    /**
     * 转换分页结果，并将每条记录由持久化对象映射为目标类型。
     */
    public static <S, T> PageResponse<T> convert(IPage<S> page, Function<? super S, T> mapper) {
        Objects.requireNonNull(page, "page must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<S> sourceRecords = page.getRecords() == null ? Collections.emptyList() : page.getRecords();
        List<T> records = sourceRecords.stream().map(mapper).toList();
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }
}
