package com.pcbqms.inspection.application;

import java.util.List;

public record ScrapImportProgress(int total, int processed, int imported, int unmatched, int invalid,
                                  List<String> errors) {
    public static ScrapImportProgress empty() {
        return new ScrapImportProgress(0, 0, 0, 0, 0, List.of());
    }
}
