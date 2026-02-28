package com.sorted.portal.assisting.beans.config;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProductCarouselBean {
    private String title;
    private String subtitle;
    private List<ProductBean> products;
}
