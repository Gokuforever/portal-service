package com.sorted.portal.response.beans;

import lombok.Builder;

@Builder
public record AmbassadorDetails(
        String mobileNo,
        String name
) {
}
