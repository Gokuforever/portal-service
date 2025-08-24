package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record OrderItemsResBean(
        String id,
        @JsonProperty("product_id")
        String productId,
        @JsonProperty("product_name")
        String productName,
        @JsonProperty("cdn_url")
        String cdnUrl,
        @JsonProperty("product_code")
        String productCode,
        Long quantity,
        @JsonProperty("total_cost")
        BigDecimal totalCost,
        @JsonProperty("selling_price")
        BigDecimal sellingPrice,
        boolean secure
) {
}
