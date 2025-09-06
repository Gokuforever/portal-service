package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthV2Bean(
        @JsonProperty("reference_id")
        String referenceId,
        String otp,
        @JsonProperty("mobile_no")
        String mobileNo
) {

}
