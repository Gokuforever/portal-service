package com.sorted.portal.response.beans;

import lombok.Builder;

@Builder
public record AmbassadorDetails(
        String id,
        String mobileNo,
        String name,
        long referredCount,
        String couponCode
) {
}
