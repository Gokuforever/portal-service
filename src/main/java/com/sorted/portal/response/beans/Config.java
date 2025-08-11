package com.sorted.portal.response.beans;

import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.portal.assisting.beans.ProductDetailsBean;
import com.sorted.portal.assisting.beans.config.HomeProductsBean;
import lombok.*;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Config {
    private List<Category_Master> categories;
    private List<HomeProductsBean> homeProducts;
}
