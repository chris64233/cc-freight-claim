package com.chris64233.freightclaim.web.view;

import java.time.OffsetDateTime;

public record HandoverEvidenceView(
        Long id,
        int afterSegmentSeq,
        String location,
        String content,
        OffsetDateTime recordedAt,
        /** 追加该证据后的运输单证据版本。 */
        int evidenceVersionAfter) {
}
