package com.chris64233.freightclaim.api.dto;

import java.time.Instant;

import com.chris64233.freightclaim.domain.HandoverEvidence;

public record HandoverEvidenceResponse(
        Long id,
        Integer seq,
        Integer fromSegmentSeq,
        Integer toSegmentSeq,
        String evidenceRef,
        String summary,
        Instant recordedAt) {

    public static HandoverEvidenceResponse of(HandoverEvidence e) {
        return new HandoverEvidenceResponse(e.getId(), e.getSeq(), e.getFromSegmentSeq(),
                e.getToSegmentSeq(), e.getEvidenceRef(), e.getSummary(), e.getRecordedAt());
    }
}
