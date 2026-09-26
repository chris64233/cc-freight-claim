package com.chris64233.freightclaim.api.dto;

import com.chris64233.freightclaim.domain.CarrierSegment;

public record SegmentResponse(
        Long id,
        Integer seq,
        String carrierCode,
        String carrierName,
        String startLocation,
        String endLocation) {

    public static SegmentResponse of(CarrierSegment s) {
        return new SegmentResponse(s.getId(), s.getSeq(), s.getCarrierCode(), s.getCarrierName(),
                s.getStartLocation(), s.getEndLocation());
    }
}
