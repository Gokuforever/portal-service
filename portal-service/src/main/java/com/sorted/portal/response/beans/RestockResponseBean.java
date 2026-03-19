package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record RestockResponseBean(
        @JsonProperty("restock_request_on")
        String restockRequestOn,
        @JsonProperty("product_master_id")
        String productMasterId,
        @JsonProperty("cdn_url")
        String cdnUrl,
        @JsonProperty("product_name")
        String productName,
        boolean read,
        @JsonProperty("notification_id")
        String notificationId
) {
}
