package com.sorted.portal.assisting.beans.config;

import lombok.Builder;

import java.math.BigDecimal;


@Builder
public record ProductBean(
        String name,
        String id,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        String image,
        Long quantity,
        Boolean secure
) {

}
