package com.pcbqms.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "qms.ocr", name = "enabled", havingValue = "true")
public class HttpOcrExtractionClient implements OcrExtractionClient {
    private final String extractionUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public HttpOcrExtractionClient(@Value("${qms.ocr.base-url}") String baseUrl) {
        this.extractionUrl = baseUrl.replaceAll("/$", "") + "/v1/extractions/work-order";
    }

    @Override
    public OcrExtractionResult extract(MultipartFile image) {
        try {
            ByteArrayResource resource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename() == null ? "erp-screenshot.png" : image.getOriginalFilename();
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.parseMediaType(
                image.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : image.getContentType()));
            body.add("image", new HttpEntity<>(resource, partHeaders));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<OcrHttpResponse> response = restTemplate.postForEntity(
                extractionUrl, new HttpEntity<>(body, headers), OcrHttpResponse.class);
            OcrHttpResponse result = response.getBody();
            if (result == null) {
                return OcrExtractionResult.empty();
            }
            return new OcrExtractionResult(result.fields() == null ? Map.of() : result.fields(),
                result.rawText() == null ? "" : result.rawText(), result.confidence());
        } catch (IOException | RuntimeException exception) {
            log.warn("OCR 服务不可用，已转人工确认：{}", exception.getMessage());
            return OcrExtractionResult.empty();
        }
    }

    private record OcrHttpResponse(Map<String, String> fields, @JsonProperty("raw_text") String rawText, double confidence) {
    }
}
