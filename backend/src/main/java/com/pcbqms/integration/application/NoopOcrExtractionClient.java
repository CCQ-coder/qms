package com.pcbqms.integration.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(prefix = "qms.ocr", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopOcrExtractionClient implements OcrExtractionClient {
    @Override
    public OcrExtractionResult extract(MultipartFile image) {
        return OcrExtractionResult.empty();
    }
}

