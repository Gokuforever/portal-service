package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MakeAmbassadorBean(
        @JsonProperty("user_id")
        String userId
) {
}
