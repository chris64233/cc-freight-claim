package com.chris64233.freightclaim.web.request;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public record EvidenceRequest(
        /** 交接发生在第几个承运段之后；缺省取当前承运段总数。 */
        Integer afterSegmentSeq,
        String location,
        @NotBlank String content,
        OffsetDateTime recordedAt) {
}
