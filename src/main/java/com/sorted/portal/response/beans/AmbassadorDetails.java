package com.sorted.portal.response.beans;

import lombok.Builder;

@Builder
public record AmbassadorDetails(
        String id,
        String mobileNo,
        String emailId,
        String name,
        long signupCount,
        long referredCount,
        String couponCode
) {
}
