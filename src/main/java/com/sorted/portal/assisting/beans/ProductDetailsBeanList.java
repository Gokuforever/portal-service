package com.sorted.portal.assisting.beans;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ProductDetailsBeanList(String name, String id, BigDecimal mrp, BigDecimal sellingPrice, Long quantity,
                                     String image, String categoryId, Boolean secure, Integer groupId) {

}
