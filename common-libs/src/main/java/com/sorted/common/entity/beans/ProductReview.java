package com.sorted.common.entity.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.mongodb.core.mapping.Field;

@Builder
@Getter
public class ProductReview {
    @Field("user_id")
    @JsonProperty("user_id")
    private String userId;
    @Field("user_name")
    @JsonProperty("user_name")
    private String userName;
    private String title;
    private String review;
    private int rating;
}
