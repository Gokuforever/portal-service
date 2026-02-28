package com.sorted.portal.request.beans;

import lombok.Builder;

@Builder
public record ReferralCodeDetails(

        String id,
        String mobile,
        String code,
        int count,
        String name
) {
}
