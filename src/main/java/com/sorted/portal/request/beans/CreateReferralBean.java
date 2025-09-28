package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.NonNull;

@Builder
public record CreateReferralBean(
        @NonNull
        @JsonProperty("user_id")
        String userId,
        @NonNull
        String code
) {
}
