package com.pcbqms.integration.application;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FlyingProbeIniParserTest {
    @Test void reproducesCSharpEventIncludingSubsecondIdentityAndChineseNames() {
        var event = new FlyingProbeIniParser().parse("飞针01", "任务一", 134065221001234560L, 134065257009876544L,
            "[Other]\r\nTest_Counters 3 2 1 25:00:00\r\n".getBytes(StandardCharsets.UTF_8));
        assertThat(event.machineName()).isEqualTo("飞针01");
        assertThat(event.materialNo()).isEqualTo("任务一");
        assertThat(event.deviceTime().toString()).isEqualTo("2025-11-02T10:55");
        assertThat(event.attributes()).containsEntry("sourceFile", "飞针01/任务一.txt")
            .containsEntry("testedBoardCount", 3).containsEntry("goodBoardCount", 2)
            .containsEntry("startTime", "2025-11-02T09:55:00").containsEntry("updatedAt", "2025-11-02T10:55:00")
            .containsEntry("badBoardCount", 1).containsEntry("lastTestTime", "25:00:00");
        assertThat(event.recordNo()).isEqualTo("FPT-4db0b098a63dd10c9e194e72c0ec92160f72707ff1a3309d1d73a617e89e37c7");
    }
    @Test void acceptsUnicodeWhitespaceAndUtf16BomLikeTheLegacyFileReader() {
        String value = "\u00a0Test_Counters\u00852 2 0 01:27:09";
        for (byte[] bytes : new byte[][] {value.getBytes(StandardCharsets.UTF_8),
                ("\ufeff" + value).getBytes(StandardCharsets.UTF_16LE)}) {
            assertThat(new FlyingProbeIniParser().parse("A", "B", 134065221000000000L, 134065221000000000L, bytes)
                .machiningCount()).isEqualTo(2);
        }
    }

    @Test void rejectsMissingDuplicateMalformedOrOverflowCounters() {
        for (String value : new String[]{"", "Test_Counters 2 2 0", "Test_Counters 2 2 0 01:60:00", "Test_Counters -1 0 0 01:00:00", "Test_Counters 2147483648 0 0 01:00:00", "Test_Counters 2 2 0 01:00:00\nTest_Counters 2 2 0 01:00:00"}) {
            assertThatThrownBy(() -> new FlyingProbeIniParser().parse("A", "B", 134065221000000000L, 134065221000000000L,
                value.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
