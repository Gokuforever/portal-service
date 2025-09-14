package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.beans.UsersBean;
import lombok.Builder;

@Builder
public record CompleteProfileRes(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("user_info")
        UsersBean userInfo,
        @JsonProperty("otp_verification_required")
        boolean otpVerificationRequired,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("redirection_url")
        String redirectionUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("reference_id")
        String referenceId
) {

}
