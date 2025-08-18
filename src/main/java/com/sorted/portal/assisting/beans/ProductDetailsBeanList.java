package com.sorted.portal.assisting.beans;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductDetailsBeanList {

    private String name;
    private String id;
    private BigDecimal mrp;
    private BigDecimal sellingPrice;
    private Long quantity;
    private String image;
    private String categoryId;
    private Boolean secure;
    private Integer groupId;
}
