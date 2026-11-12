package com.pcbqms.inspection.application;

import com.pcbqms.workorder.domain.WorkOrder;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

final class ScrapWorkOrderMatcher {
    private ScrapWorkOrderMatcher() { }

    static Optional<WorkOrder> closest(String internalProductNo, LocalDate excelDate, List<WorkOrder> orders) {
        List<WorkOrder> eligible = orders.stream()
            .filter(order -> matches(internalProductNo, order.getInternalProductNo()))
            .filter(order -> order.getInputDate() != null && !order.getInputDate().isAfter(excelDate))
            .toList();
        LocalDate latest = eligible.stream().map(WorkOrder::getInputDate).max(Comparator.naturalOrder()).orElse(null);
        if (latest == null) return Optional.empty();
        List<WorkOrder> closest = eligible.stream().filter(order -> latest.equals(order.getInputDate())).toList();
        return closest.size() == 1 ? Optional.of(closest.getFirst()) : Optional.empty();
    }

    static boolean matches(String imported, String workOrderInternalNo) {
        if (imported == null || workOrderInternalNo == null) return false;
        String key = imported.trim().toUpperCase(Locale.ROOT);
        String candidate = workOrderInternalNo.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return false;
        if (key.length() == 5 && key.chars().allMatch(Character::isDigit)) {
            for (int index = candidate.indexOf(key); index >= 0; index = candidate.indexOf(key, index + 1)) {
                int end = index + key.length();
                if ((index == 0 || !Character.isDigit(candidate.charAt(index - 1)))
                    && (end == candidate.length() || !Character.isDigit(candidate.charAt(end)))) return true;
            }
            return false;
        }
        return candidate.contains(key);
    }
}
