package com.chris64233.freightclaim.web.view;

import java.time.OffsetDateTime;

/** 证据台账中的单条证据。 */
public record EvidenceView(
        /** HANDOVER=运输单交接证据，CLAIM=索赔自带证据。 */
        String kind,
        String externalClaimNo,
        Integer afterSegmentSeq,
        String location,
        String content,
        OffsetDateTime recordedAt) {
}
