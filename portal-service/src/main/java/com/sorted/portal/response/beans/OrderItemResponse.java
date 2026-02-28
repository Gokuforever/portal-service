package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record OrderItemResponse(@JsonProperty("product_id") String productId,
                                @JsonProperty("product_name") String productName,
                                @JsonProperty("cdn_url") String cdnUrl,
                                @JsonProperty("product_code") String productCode, Long quantity,
                                @JsonProperty("selling_price") Long sellingPrice,
                                @JsonProperty("total_cost") Long totalCost,
                                @JsonProperty("purchase_type") String purchaseType) {
}
