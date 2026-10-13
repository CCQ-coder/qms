package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.Capa;
import java.time.LocalDateTime;

public record CapaResponse(
    String id,
    String capaNo,
    String ncrNo,
    String status,
    String problemDescription,
    String teamMembers,
    String containmentAction,
    String rootCause,
    String correctiveAction,
    String preventiveAction,
    String verificationResult,
    String recognitionNotes,
    String initiatedBy,
    String approvedBy,
    LocalDateTime createdAt,
    LocalDateTime closedAt
) {
    public static CapaResponse from(Capa entity, String ncrNo) {
        return new CapaResponse(entity.getId(), entity.getCapaNo(), ncrNo, entity.getStatus(),
            entity.getProblemDescription(), entity.getTeamMembers(), entity.getContainmentAction(), entity.getRootCause(),
            entity.getCorrectiveAction(), entity.getPreventiveAction(), entity.getVerificationResult(), entity.getRecognitionNotes(),
            entity.getInitiatedBy(), entity.getApprovedBy(),
            entity.getCreatedAt(), entity.getClosedAt());
    }
}
