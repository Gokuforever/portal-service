package com.sorted.common.beans;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ProductBean(
        String name,
        String id,
        String productMasterId,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        String image,
        Long quantity,
        Boolean secure,
        Boolean isRestockNotificationEnabled
) {

}
