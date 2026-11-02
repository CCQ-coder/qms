package com.pcbqms.integration.application;

import com.pcbqms.equipment.api.EquipmentTelemetryEventRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FlyingProbeIniParser {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public EquipmentTelemetryEventRequest parse(String machine, String job, long createdFileTime,
            long modifiedFileTime, byte[] content) {
        String[] counters = null;
        // Counter tokens are ASCII in both legacy ANSI files and UTF-8 INI files.
        for (String line : decode(content).split("\\r\\n|\\r|\\n")) {
            String[] values = line.replaceFirst("(?U)^\\s+", "").split("(?U)\\s+");
            if (!values[0].equals("Test_Counters")) continue;
            if (counters != null || values.length != 5) throw invalid();
            counters = values;
        }
        if (counters == null || !counters[4].matches("[0-9]+:[0-5][0-9]:[0-5][0-9]")) throw invalid();
        int tested = count(counters[1]), good = count(counters[2]), bad = count(counters[3]);
        LocalDateTime created = localTime(createdFileTime), modified = localTime(modifiedFileTime);
        String source = machine + "/" + job + ".txt";
        StringBuilder upper = new StringBuilder();
        source.codePoints().map(Character::toUpperCase).forEach(upper::appendCodePoint);
        String identity = String.join("\n", upper.toString(), Long.toString(ticks(created)), Long.toString(ticks(modified)),
            Integer.toString(tested), Integer.toString(good), Integer.toString(bad), counters[4]);
        String recordNo = "FPT-" + sha256(identity.getBytes(StandardCharsets.UTF_8));
        return new EquipmentTelemetryEventRequest(null, machine, modified.truncatedTo(ChronoUnit.SECONDS),
            "FLYING_PROBE_TEST_COUNTERS", null, recordNo, tested, null, null, job, null, null, null,
            null, null, null, null, Map.of("sourceProtocol", "FLYING_PROBE_TXT_V1",
                "startTime", TIME.format(created), "updatedAt", TIME.format(modified),
                "testedBoardCount", tested, "goodBoardCount", good, "badBoardCount", bad,
                "lastTestTime", counters[4], "sourceFile", source));
    }

    private String decode(byte[] content) {
        java.nio.charset.Charset charset = StandardCharsets.UTF_8;
        if (content.length >= 4 && ((content[0] == -1 && content[1] == -2 && content[2] == 0 && content[3] == 0)
            || (content[0] == 0 && content[1] == 0 && content[2] == -2 && content[3] == -1))) {
            charset = java.nio.charset.Charset.forName("UTF-32");
        } else if (content.length >= 2 && ((content[0] == -1 && content[1] == -2)
            || (content[0] == -2 && content[1] == -1))) {
            charset = StandardCharsets.UTF_16;
        }
        String text = new String(content, charset);
        return text.startsWith("\ufeff") ? text.substring(1) : text;
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int count(String value) {
        if (!value.matches("[0-9]+")) throw invalid();
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw invalid(); }
    }

    private LocalDateTime localTime(long fileTime) {
        if (fileTime < 0 || fileTime > 2_650_467_743_999_999_999L) throw invalid();
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(
            fileTime / 10_000_000 - 11_644_473_600L, fileTime % 10_000_000 * 100), ZoneId.of("Asia/Shanghai"));
        if (time.getYear() > 9999) throw invalid();
        return time;
    }

    private long ticks(LocalDateTime time) {
        return ChronoUnit.DAYS.between(LocalDate.of(1, 1, 1), time.toLocalDate()) * 864_000_000_000L
            + time.toLocalTime().toNanoOfDay() / 100;
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("无效的 Test_Counters 或 FILETIME");
    }
}
