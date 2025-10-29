package com.sorted.portal.service;

import com.sorted.commons.beans.SelectedSubCategories;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.mongo.Combo;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.ComboService;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.helper.Pagination;
import com.sorted.commons.helper.SearchHistoryAsyncHelper;
import com.sorted.commons.utils.ComboUtility;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.assisting.beans.ProductDetailsBeanList;
import com.sorted.portal.request.beans.FindProductBean;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreProductService {

    private final ComboService comboService;
    private final ProductService productService;
    @Value("${se.default.seller:68711a63a2dcdf55ed170972}")
    private String defaultSeller;
    private final SearchHistoryAsyncHelper searchHistoryAsyncHelper;
    private final Category_MasterService category_MasterService;
    private final ComboUtility comboUtility;
    @Value("${se.store.allowed.categories:660194cde437f74a756be5f7,6858628aa520924ecbaa7ad5,687b6f241e9e6eb839f72cd5,687c94224323c53b054eafea}")
    private String allowedCategories;

    public List<ProductDetailsBeanList> getProductDetailsBeanLists(FindProductBean req, UsersBean usersBean) {
        List<ProductDetailsBeanList> comboProducts = new ArrayList<>();
        SEFilter filterSE = new SEFilter(SEFilterType.AND);
        SEFilter filterCombo = new SEFilter(SEFilterType.AND);
        filterSE.addClause(WhereClause.eq(Products.Fields.seller_id, defaultSeller));
        String name = req.getName();
        if (StringUtils.hasText(name)) {
            String productName = name.trim().replaceAll("\\s+", " ");
            filterSE.addClause(WhereClause.like(Products.Fields.name, productName));
            filterCombo.addClause(WhereClause.like(Combo.Fields.name, productName));
            filterCombo.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Combo> combos = comboService.repoFind(filterCombo);
            if (!CollectionUtils.isEmpty(combos)) {
                Map<String, Long> resultMap = combos.stream()
                        .collect(Collectors.toMap(
                                Combo::getId,
                                combo -> {
                                    List<Products> products = comboUtility.getProductsByCombo(combo);
                                    return products.stream().map(Products::getQuantity).toList().stream().sorted().toList().get(0);
                                }
                        ));
                comboProducts.addAll(combos.stream().map(combo -> this.getResponseBean(combo, resultMap.get(combo.getId()))).toList());
            }
        }
        List<String> allowedCategoryList = List.of(this.allowedCategories.split(","));
        if (StringUtils.hasText(req.getCategory_id()) && allowedCategoryList.contains(req.getCategory_id())) {
            filterSE.addClause(WhereClause.eq(Products.Fields.category_id, req.getCategory_id()));
        } else {
            filterSE.addClause(WhereClause.in(Products.Fields.category_id, allowedCategoryList));
        }

        if (req.getGroup_id() != null && req.getGroup_id() > 0) {
            filterSE.addClause(WhereClause.eq(Products.Fields.group_id, req.getGroup_id()));
        }
        if (!CollectionUtils.isEmpty(req.getFilters())) {
            List<SEFilterNode> filterNodes = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : req.getFilters().entrySet()) {
                entry.getValue().removeIf(e -> !StringUtils.hasText(e));
                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                    SEFilterNode node = new SEFilterNode(SEFilterType.OR);
                    Map<String, Object> map = new HashMap<>();
                    map.put(SelectedSubCategories.Fields.sub_category, entry.getKey());
                    map.put(SelectedSubCategories.Fields.selected_attributes, entry.getValue());
                    node.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
                    filterNodes.add(node);
                }
            }
            filterSE.addNodes(filterNodes);
        }
        filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (StringUtils.hasText(req.getSort_by())) {
            OrderBy sort = switch (req.getSort_by()) {
                case "price_low_to_high" -> new OrderBy(Products.Fields.selling_price, SortOrder.ASC);
                case "price_high_to_low" -> new OrderBy(Products.Fields.selling_price, SortOrder.DESC);
                case "newest" -> new OrderBy(BaseMongoEntity.Fields.creation_date, SortOrder.DESC);
                case "oldest" -> new OrderBy(BaseMongoEntity.Fields.creation_date, SortOrder.ASC);
                default -> new OrderBy(BaseMongoEntity.Fields.modification_date, SortOrder.DESC);
            };
            filterSE.setOrderBy(sort);
        }
        searchHistoryAsyncHelper.createSearchHistory(usersBean.getId(), usersBean.getRole().getUser_type_id(),
                filterSE);
        List<Products> listP = productService.repoFind(filterSE);
        if (CollectionUtils.isEmpty(listP)) {
            return comboProducts;
        }
        List<ProductDetailsBeanList> list = new ArrayList<>();
        for (Products p : listP) {
            list.add(getResponseBean(p));
        }
        list.addAll(comboProducts);
        return list;
    }

    public List<ProductDetailsBeanList> getRelatedProducts(Products product) {

        Category_Master category_Master = category_MasterService.findById(product.getCategory_id()).orElseThrow();
        List<String> list_filterable = category_Master.getGroups().stream()
                .flatMap(e -> e.getSub_categories().stream()).filter(Category_Master.SubCategory::isRelated_filterable).map(Category_Master.SubCategory::getName)
                .toList();
        Map<String, List<String>> relatedFilters = new HashMap<>();

        product.getSelected_sub_catagories().stream().filter(e -> list_filterable.contains(e.getSub_category()))
                .forEach(s -> {
                    if (!relatedFilters.containsKey(s.getSub_category())) {
                        relatedFilters.put(s.getSub_category(), new ArrayList<>());
                    }
                    relatedFilters.get(s.getSub_category()).addAll(s.getSelected_attributes());
                });

        SEFilter filterRI = new SEFilter(SEFilterType.AND);
        if (!CollectionUtils.isEmpty(relatedFilters)) {
            this.makeValuesUnique(relatedFilters);

            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : relatedFilters.entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                    map.put(SelectedSubCategories.Fields.sub_category, entry.getKey());
                    map.put(SelectedSubCategories.Fields.selected_attributes, entry.getValue());
                }
            }
            if (!map.isEmpty()) {
                filterRI.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
            }
        }
        filterRI.addClause(WhereClause.eq(Products.Fields.group_id, product.getGroup_id()));
        filterRI.addClause(WhereClause.notEq(BaseMongoEntity.Fields.id, product.getId()));
        filterRI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Pagination pagination = new Pagination(0, 12);
        filterRI.setPagination(pagination);

        List<Products> listRI = productService.repoFind(filterRI);
        if (CollectionUtils.isEmpty(listRI)) {
            return null;
        }

        List<ProductDetailsBeanList> list = new ArrayList<>();
        for (Products p : listRI) {
            list.add(getResponseBean(p));
        }
        return list;

    }

    public ProductDetailsBeanList getResponseBean(Combo c, long quantity) {
        return ProductDetailsBeanList.builder()
                .name(c.getName())
                .id(c.getId())
                .mrp(CommonUtils.paiseToRupee(c.getMrp()))
                .sellingPrice(CommonUtils.paiseToRupee(c.getSelling_price()))
                .quantity(quantity)
                .image(CollectionUtils.isEmpty(c.getMedia()) ? "" : c.getMedia().get(0).getCdn_url())
                .secure(false)
                .is_combo(true)
                .build();
    }

    public ProductDetailsBeanList getResponseBean(Products p) {
        return ProductDetailsBeanList.builder()
                .name(p.getName())
                .id(p.getId())
                .mrp(CommonUtils.paiseToRupee(p.getMrp()))
                .sellingPrice(CommonUtils.paiseToRupee(p.getSelling_price()))
                .quantity(p.getQuantity())
                .image(CollectionUtils.isEmpty(p.getMedia()) ? "" : p.getMedia().stream().filter(e -> e.getOrder() == 0).findFirst().get().getCdn_url())
                .categoryId(p.getCategory_id())
                .groupId(p.getGroup_id())
                .secure(false)
                .search_subtitle(p.getSelected_sub_catagories().get(0).getSelected_attributes().get(0))
                .build();
    }

    private void makeValuesUnique(Map<String, List<String>> map) {
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            List<String> uniqueList = new ArrayList<>(new HashSet<>(entry.getValue()));
            entry.setValue(uniqueList);
        }
    }
}
