package com.lzj.railway.framework.idgenerator.snowflake;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerNodeAssignerTest {

    @Test
    void shouldReturnConfiguredFixedWorkerNode() {
        WorkerNode workerNode = new FixedWorkerNodeAssigner(66).assign();

        assertThat(workerNode).isEqualTo(new WorkerNode(66));
    }

    @Test
    void shouldExtractWorkerIdFromStatefulSetHostname() {
        WorkerNode workerNode = new HostnameWorkerNodeAssigner("ticket-service-12").assign();

        assertThat(workerNode).isEqualTo(new WorkerNode(12));
    }

    @Test
    void shouldRejectHostnameWithoutNumericOrdinal() {
        assertThatThrownBy(() -> new HostnameWorkerNodeAssigner("ticket-service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hostname must end with a numeric ordinal");
    }

    @Test
    void shouldRejectHostnameOrdinalOutsideNodeIdRange() {
        assertThatThrownBy(() -> new HostnameWorkerNodeAssigner("ticket-service-1024"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("node id must be between 0 and 1023");
    }
}
