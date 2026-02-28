package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartBeanV2 {
    @JsonProperty("cart_items")
    private List<CartItems> cartItems;
    @JsonProperty("billing_summary")
    private BillingSummary billingSummary;
    @JsonProperty("is_free_delivery")
    private boolean isFreeDelivery;
    @JsonProperty("store_operational")
    private boolean isStoreOperational;
    @JsonProperty("total_item_count")
    private long totalItemCount;
}
