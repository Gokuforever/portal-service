package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Recommendations {
    @JsonProperty("product_id")
    private String productId;
    private String title;
    private String text;
    private int rating;
}
