package com.sorted.portal.assisting.beans.config;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductBean {
    private String name;
    private String id;
    private double mrp;
    private double sellingPrice;
    private String image;
}
