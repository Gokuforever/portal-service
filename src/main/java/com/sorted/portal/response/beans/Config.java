package com.sorted.portal.response.beans;

import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.portal.assisting.beans.config.HomeProductsBean;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Config {
    private List<Category_Master> categories;
    private List<HomeProductsBean> homeProducts;
}
