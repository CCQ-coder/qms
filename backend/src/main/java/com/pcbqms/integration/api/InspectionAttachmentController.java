package com.pcbqms.integration.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.integration.application.InspectionAttachmentApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class InspectionAttachmentController {
    private final InspectionAttachmentApplicationService inspectionAttachmentApplicationService;

    @PostMapping("/api/v1/inspection-attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InspectionAttachmentResponse> upload(@RequestPart("image") MultipartFile image) {
        return ApiResponse.of(inspectionAttachmentApplicationService.upload(image));
    }

    @GetMapping("/api/v1/attachments/{id}")
    public ResponseEntity<FileSystemResource> view(@PathVariable String id) {
        InspectionAttachmentApplicationService.AttachmentFile attachment = inspectionAttachmentApplicationService.load(id);
        MediaType mediaType = MediaTypeFactory.getMediaType(attachment.originalFilename())
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", ContentDisposition.inline().filename(attachment.originalFilename()).build().toString())
            .body(new FileSystemResource(attachment.file()));
    }
}
