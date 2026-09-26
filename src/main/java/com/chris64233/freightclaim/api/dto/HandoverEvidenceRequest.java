package com.chris64233.freightclaim.api.dto;

import jakarta.validation.constraints.NotNull;

/** 追加交接证据。seq 由服务端按到达顺序分配。 */
public record HandoverEvidenceRequest(
        Integer fromSegmentSeq,
        @NotNull Integer toSegmentSeq,
        String evidenceRef,
        String summary) {
}
