package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FlyingProbeCountsTest {
    @Test
    void producesOnlyNewBoardCountsFromCumulativeSnapshots() {
        FlyingProbeCounts first = new FlyingProbeCounts(3, 3, 0);
        FlyingProbeCounts second = new FlyingProbeCounts(5, 4, 1);

        assertThat(first.deltaFrom(FlyingProbeCounts.ZERO)).isEqualTo(first);
        assertThat(second.deltaFrom(first)).isEqualTo(new FlyingProbeCounts(2, 1, 1));
        assertThat(second.deltaFrom(second)).isEqualTo(FlyingProbeCounts.ZERO);
    }

    @Test
    void rejectsInconsistentOrRegressingCounts() {
        assertThatThrownBy(() -> new FlyingProbeCounts(5, 3, 1).deltaFrom(FlyingProbeCounts.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlyingProbeCounts(2, 2, 0).deltaFrom(new FlyingProbeCounts(3, 3, 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
