package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SendOtpReq(
        @JsonProperty("mobile_no")
        String mobileNo
) {
}
