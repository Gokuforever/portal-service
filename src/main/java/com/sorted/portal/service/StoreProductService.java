package com.sorted.portal.service;

import com.sorted.commons.beans.SelectedSubCatagories;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.SearchHistoryAsyncHelper;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.assisting.beans.ProductDetailsBeanList;
import com.sorted.portal.request.beans.FindProductBean;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StoreProductService {

    private final ProductService productService;
    @Value("${se.default.seller:68711a63a2dcdf55ed170972}")
    private String defaultSeller;
    private final SearchHistoryAsyncHelper searchHistoryAsyncHelper;

    public List<ProductDetailsBeanList> getProductDetailsBeanLists(FindProductBean req, UsersBean usersBean) {
        AggregationFilter.SEFilter filterSE = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterSE.addClause(AggregationFilter.WhereClause.eq(Products.Fields.seller_id, defaultSeller));
        if (StringUtils.hasText(req.getName())) {
            filterSE.addClause(AggregationFilter.WhereClause.like(Products.Fields.name, req.getName()));
        }
        if (StringUtils.hasText(req.getCategory_id())) {
            filterSE.addClause(AggregationFilter.WhereClause.eq(Products.Fields.category_id, req.getCategory_id()));
        }
        if (req.getGroup_id() != null && req.getGroup_id() > 0) {
            filterSE.addClause(AggregationFilter.WhereClause.eq(Products.Fields.group_id, req.getGroup_id()));
        }
        if (!CollectionUtils.isEmpty(req.getFilters())) {
            for (Map.Entry<String, List<String>> entry : req.getFilters().entrySet()) {
                entry.getValue().removeIf(e -> !StringUtils.hasText(e));
                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                    Map<String, Object> map = new HashMap<>();
                    map.put(SelectedSubCatagories.Fields.sub_category, entry.getKey());
                    map.put(SelectedSubCatagories.Fields.selected_attributes, entry.getValue());
                    filterSE.addClause(AggregationFilter.WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
                }
            }
        }
        filterSE.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (StringUtils.hasText(req.getSort_by())) {
            AggregationFilter.OrderBy sort = switch (req.getSort_by()) {
                case "price_low_to_high" ->
                        new AggregationFilter.OrderBy(Products.Fields.selling_price, AggregationFilter.SortOrder.ASC);
                case "price_high_to_low" ->
                        new AggregationFilter.OrderBy(Products.Fields.selling_price, AggregationFilter.SortOrder.DESC);
                case "newest" ->
                        new AggregationFilter.OrderBy(BaseMongoEntity.Fields.creation_date, AggregationFilter.SortOrder.DESC);
                case "oldest" ->
                        new AggregationFilter.OrderBy(BaseMongoEntity.Fields.creation_date, AggregationFilter.SortOrder.ASC);
                default ->
                        new AggregationFilter.OrderBy(BaseMongoEntity.Fields.modification_date, AggregationFilter.SortOrder.DESC);
            };
            filterSE.setOrderBy(sort);
        }
        searchHistoryAsyncHelper.createSearchHistory(usersBean.getId(), usersBean.getRole().getUser_type_id(),
                filterSE);
        List<Products> listP = productService.repoFind(filterSE);
        if (CollectionUtils.isEmpty(listP)) {
            return Collections.emptyList();
        }
        List<ProductDetailsBeanList> list = new ArrayList<>();
        for (Products p : listP) {
            list.add(getResponseBean(p));
        }
        return list;
    }

    private static ProductDetailsBeanList getResponseBean(Products p) {
        return ProductDetailsBeanList.builder()
                .name(p.getName())
                .id(p.getId())
                .mrp(CommonUtils.paiseToRupee(p.getMrp()))
                .sellingPrice(CommonUtils.paiseToRupee(p.getSelling_price()))
                .quantity(p.getQuantity())
                .image(CollectionUtils.isEmpty(p.getMedia()) ? "" : p.getMedia().stream().filter(e -> e.getOrder() == 0).findFirst().get().getCdn_url())
                .categoryId(p.getCategory_id())
                .groupId(p.getGroup_id())
                .secure(p.getIs_secure())
                .build();
    }
}
