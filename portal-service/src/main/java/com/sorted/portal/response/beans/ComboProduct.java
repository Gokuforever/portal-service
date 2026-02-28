package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ComboProduct(
        @JsonProperty("product_id")
        String productId,
        @JsonProperty("product_name")
        String productName,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("cdn_url")
        String cdnUrl,
        @JsonProperty("product_code")
        String productCode,
        @JsonProperty("selling_price")
        BigDecimal sellingPrice,
        BigDecimal mrp,
        @JsonProperty("quantity")
        Long quantity
) {
}
