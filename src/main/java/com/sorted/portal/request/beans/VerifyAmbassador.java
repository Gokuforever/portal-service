package com.sorted.portal.request.beans;


import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifyAmbassador(
        @JsonProperty("reference_id")
        String referenceId,
        String otp,
        @JsonProperty("mobile_no")
        String mobileNo,
        @JsonProperty("entity_id") String entityId

) {
}
