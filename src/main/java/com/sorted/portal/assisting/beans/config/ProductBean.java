package com.sorted.portal.assisting.beans.config;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductBean {
    private String name;
    private String id;
    private BigDecimal mrp;
    private BigDecimal sellingPrice;
    private String image;
}
