package com.sorted.common.utils;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Products;
import com.sorted.common.entity.service.ProductService;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@RequiredArgsConstructor
@Service
public class ProductUtility {

    private final ProductService productService;

    public Map<String, Long> getProductHighestSellingPrice(String... productMasterIds) {

        if (productMasterIds == null || productMasterIds.length == 0) {
            return Collections.emptyMap();
        }

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(Products.Fields.product_master_id, Arrays.asList(productMasterIds)));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Products> products = productService.repoFind(filter);

        if (CollectionUtils.isEmpty(products)) {
            return Collections.emptyMap();
        }

        return getMaxSellingPrice(products);
    }

    private static Map<String, Long> getMaxSellingPrice(List<Products> products) {

        Map<String, Long> maxSPMap = new HashMap<>();

        for (Products product : products) {
            maxSPMap.merge(
                    product.getProduct_master_id(),
                    product.getSelling_price(),
                    Math::max
            );
        }

        return maxSPMap;
    }


}
