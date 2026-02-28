package com.sorted.common.entity.beans;

import lombok.Builder;
import org.springframework.data.mongodb.core.mapping.Field;

@Builder
public class DisplaySettings {

    @Field("display_order")
    private String displayOrder;
    @Field("show_on_product_page")
    private Boolean showOnProductPage;
    @Field("show_in_category_page")
    private Boolean showInCategoryPage;
    @Field("homepage_featured")
    private Boolean homepageFeatured;
}
