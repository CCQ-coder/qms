package com.pcbqms.masterdata.api;

import com.pcbqms.masterdata.domain.ProcessMaster;

public record ProcessResponse(String id, String processCode, String processName, Integer sequenceNo, String status) {
    public static ProcessResponse from(ProcessMaster process) {
        return new ProcessResponse(process.getId(), process.getProcessCode(), process.getProcessName(),
            process.getSequenceNo(), process.getStatus());
    }
}
