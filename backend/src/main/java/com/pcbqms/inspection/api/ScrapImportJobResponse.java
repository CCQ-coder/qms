package com.pcbqms.inspection.api;

import java.time.LocalDateTime;
import java.util.List;

public record ScrapImportJobResponse(String id, String filename, String status, int total, int processed,
                                     int imported, int unmatched, int invalid, List<String> errors,
                                     LocalDateTime createdAt) { }
