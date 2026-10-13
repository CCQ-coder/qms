package com.pcbqms.integration.application;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface OcrExtractionClient {
    OcrExtractionResult extract(MultipartFile image);

    record OcrExtractionResult(Map<String, String> fields, String rawText, double confidence) {
        public static OcrExtractionResult empty() {
            return new OcrExtractionResult(Map.of(), "", 0D);
        }
    }
}

