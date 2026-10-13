package com.pcbqms.integration.api;

import java.util.Map;

public record OcrUploadResponse(String sourceDocumentId, String originalFilename, String status,
                                Map<String, String> extractedFields, String message) {
}
