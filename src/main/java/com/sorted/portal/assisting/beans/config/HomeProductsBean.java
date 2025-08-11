package com.sorted.portal.assisting.beans.config;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HomeProductsBean {
    private String categoryId;
    private String mainTitle;
    private String mainSubtitle;
    private ProductCarouselBean productCarousel;
    private List<GroupComponentBean> groupComponent;
}
