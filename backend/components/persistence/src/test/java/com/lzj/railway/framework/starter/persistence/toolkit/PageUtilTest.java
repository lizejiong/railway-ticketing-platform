package com.lzj.railway.framework.starter.persistence.toolkit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzj.railway.framework.convention.page.PageRequest;
import com.lzj.railway.framework.convention.page.PageResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageUtilTest {

    @Test
    void shouldConvertPageRequest() {
        Page<String> page = PageUtil.convert(new PageRequest(3, 20));

        assertThat(page.getCurrent()).isEqualTo(3);
        assertThat(page.getSize()).isEqualTo(20);
    }

    @Test
    void shouldConvertPageResponseWithoutChangingRecords() {
        Page<String> page = new Page<>(2, 5, 12);
        page.setRecords(List.of("G1", "G2"));

        PageResponse<String> response = PageUtil.convert(page);

        assertThat(response.getCurrent()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(5);
        assertThat(response.getTotal()).isEqualTo(12);
        assertThat(response.getRecords()).containsExactly("G1", "G2");
    }

    @Test
    void shouldMapRecordTypeAndKeepPageMetadata() {
        Page<Integer> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(100, 200));

        PageResponse<String> response = PageUtil.convert(page, value -> "train-" + value);

        assertThat(response.getCurrent()).isOne();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getRecords()).containsExactly("train-100", "train-200");
    }

    @Test
    void shouldTreatNullRecordsAsEmptyList() {
        Page<String> page = new Page<>(1, 10, 0);
        page.setRecords(null);

        PageResponse<String> response = PageUtil.convert(page);

        assertThat(response.getRecords()).isEmpty();
    }
}
