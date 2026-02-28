package com.sorted.portal.response.beans;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record VerifiedUsersResBean(
        String id,
        @JsonProperty("first_name")
        String firstName,
        @JsonProperty("last_name")
        String lastName,
        String email,
        String mobile,
        @JsonProperty("created_at")
        String createdAt
) {
}
