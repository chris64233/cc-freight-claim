package com.chris64233.freightclaim.web.view;

public record SegmentView(
        Long id,
        int sequenceNo,
        String carrierCode,
        String carrierName,
        String startNode,
        String endNode) {
}
