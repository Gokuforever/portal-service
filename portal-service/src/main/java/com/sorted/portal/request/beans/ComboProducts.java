package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ComboProducts(
        String name,
        String id,
        String image,
        @JsonProperty("selling_price")
        BigDecimal sellingPrice,
        BigDecimal mrp
) {
}
