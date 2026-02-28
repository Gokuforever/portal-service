package com.sorted.portal.assisting.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record ProductReview(
        @JsonProperty("user_name")
        String userName,
        String review,
        int rating,
        String title
) {
}
