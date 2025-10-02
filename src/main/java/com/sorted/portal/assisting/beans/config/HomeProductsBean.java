package com.sorted.portal.assisting.beans.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HomeProductsBean {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String categoryId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String mainBadge;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String mainTitle;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String mainSubtitle;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ProductCarouselBean productCarousel;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<GroupComponentBean> groupComponent;
    private boolean combo;
}
