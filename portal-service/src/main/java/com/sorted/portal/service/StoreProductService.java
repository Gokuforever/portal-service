package com.sorted.portal.service;

import com.sorted.common.beans.SelectedSubCategories;
import com.sorted.common.beans.UsersBean;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.*;
import com.sorted.common.helper.AggregationFilter.*;
import com.sorted.common.helper.Pagination;
import com.sorted.common.helper.SearchHistoryAsyncHelper;
import com.sorted.common.service.ZoneHandlerService;
import com.sorted.common.utils.ComboUtility;
import com.sorted.common.utils.CommonUtils;
import com.sorted.common.utils.ProductUtility;
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
    @Value("${se.store.allowed.categories:660194cde437f74a756be5f7,693701e9c6f45220cc784671,69370ac8c6f45220cc784675,69370e15c6f45220cc784678,69370f58c6f45220cc78467a,69371231c6f45220cc78467c,68df9761ffe872a16b247617,68dd47b6f88953e8a00deea5,693edb76e48e2b76f1cb918c}")
    private String allowedCategories;
    private final RecommendationsService recommendationsService;
    private final ZoneHandlerService zoneHandlerService;
    private final Product_Master_Service productMasterService;
    private final ProductUtility productUtility;

    public List<ProductDetailsBeanList> getProductDetailsBeanLists(FindProductBean req, UsersBean usersBean) {

        String zoneId = usersBean.getNearestZoneId();
        Seller seller = zoneHandlerService.getSellerByZone(zoneId, usersBean.getCurrentLat().doubleValue(), usersBean.getCurrentLng().doubleValue());

        SEFilter filterSE = new SEFilter(SEFilterType.AND);
        filterSE.addClause(WhereClause.eq(Products.Fields.seller_id, defaultSeller));
        String name = req.getName();

        SEFilter filterPM = new SEFilter(SEFilterType.AND);
        filterPM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        if (StringUtils.hasText(name)) {
            String productName = name.trim().replaceAll("\\s+", " ");

            SEFilterNode nameNodePM = new SEFilterNode(SEFilterType.OR);
            nameNodePM.addClause(WhereClause.like(Product_Master.Fields.name, productName));
            nameNodePM.addClause(WhereClause.like(Product_Master.Fields.desc, productName));

            SEFilterNode nameNodeP = new SEFilterNode(SEFilterType.OR);
            nameNodeP.addClause(WhereClause.like(Products.Fields.name, productName));
            nameNodeP.addClause(WhereClause.like(Products.Fields.description, productName));

            filterPM.addNodes(nameNodePM);
            filterSE.addNodes(nameNodeP);
        }

        List<String> allowedCategoryList = List.of(this.allowedCategories.split(","));

        if (StringUtils.hasText(req.getCategory_id()) && allowedCategoryList.contains(req.getCategory_id())) {
            filterPM.addClause(WhereClause.eq(Product_Master.Fields.catagory_id, req.getCategory_id()));
            filterSE.addClause(WhereClause.eq(Products.Fields.category_id, req.getCategory_id()));
        } else {
            filterPM.addClause(WhereClause.in(Product_Master.Fields.catagory_id, allowedCategoryList));
            filterSE.addClause(WhereClause.in(Products.Fields.category_id, allowedCategoryList));
        }

        if (req.getGroup_id() != null && req.getGroup_id() > 0) {
            filterPM.addClause(WhereClause.eq(Product_Master.Fields.group_id, req.getGroup_id()));
            filterSE.addClause(WhereClause.eq(Products.Fields.group_id, req.getGroup_id()));
        }

        if (!CollectionUtils.isEmpty(req.getFilters())) {
            SEFilterNode node = new SEFilterNode(SEFilterType.AND);
            SEFilterNode nodePM = new SEFilterNode(SEFilterType.AND);
            for (Map.Entry<String, List<String>> entry : req.getFilters().entrySet()) {
                entry.getValue().removeIf(e -> !StringUtils.hasText(e));
                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                    Map<String, Object> map = new HashMap<>();
                    map.put(SelectedSubCategories.Fields.sub_category, entry.getKey());
                    map.put(SelectedSubCategories.Fields.selected_attributes, entry.getValue());
                    node.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
                    nodePM.addClause(WhereClause.in("sub_categories." + entry.getKey(), entry.getValue()));
                }
            }
            if (node.getClause() != null) {
                filterSE.addNodes(node);
                filterPM.addNodes(nodePM);
            }
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

            OrderBy sortPM = switch (req.getSort_by()) {
                case "price_low_to_high" -> new OrderBy(Products.Fields.mrp, SortOrder.ASC);
                case "price_high_to_low" -> new OrderBy(Products.Fields.mrp, SortOrder.DESC);
                case "oldest" -> new OrderBy(BaseMongoEntity.Fields.id, SortOrder.ASC);
                default -> new OrderBy(BaseMongoEntity.Fields.id, SortOrder.DESC);
            };
            filterSE.setOrderBy(sort);
            filterPM.setOrderBy(sortPM);
        }

        List<Product_Master> productMasters = productMasterService.repoFind(filterPM);

        searchHistoryAsyncHelper.createSearchHistory(usersBean.getId(), usersBean.getRole().getUser_type_id(),
                filterSE);
        filterSE.addClause(WhereClause.eq(Products.Fields.seller_id, seller.getId()));
        List<Products> listP = productService.repoFind(filterSE);

        List<ProductDetailsBeanList> list = new ArrayList<>();
        if (!CollectionUtils.isEmpty(listP)) {
            List<String> filteredMasterIds = listP.stream().map(Products::getProduct_master_id).toList();

            SEFilter filterAllProducts = new SEFilter(SEFilterType.AND);
            filterAllProducts.addClause(WhereClause.in(Products.Fields.product_master_id, filteredMasterIds));
            filterAllProducts.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            List<Products> allFilteredProducts = productService.repoFind(filterAllProducts);

            Map<String, List<Products>> listMap = allFilteredProducts.stream().collect(Collectors.groupingBy(Products::getProduct_master_id));
            Map<String, Long> highestPrize = productUtility.getProductHighestSellingPrice(listMap.entrySet().toArray(String[]::new));

            for (Products p : listP) {
                list.add(getResponseBean(p, highestPrize));
            }
        }

        if (!CollectionUtils.isEmpty(productMasters)) {
            List<String> productMasterIds = list.isEmpty() ? new ArrayList<>() : list.stream().map(ProductDetailsBeanList::productMasterId).toList();
            for (Product_Master productMaster : productMasters) {
                if (productMasterIds.contains(productMaster.getId())) {
                    continue;
                }
                list.add(getResponseBean(productMaster));
            }
        }
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

        Map<String, Long> highestSellingPrice = productUtility.getProductHighestSellingPrice(listRI.stream().map(Products::getProduct_master_id).toArray(String[]::new));

        List<ProductDetailsBeanList> list = new ArrayList<>();
        for (Products p : listRI) {
            list.add(getResponseBean(p, highestSellingPrice));
        }
        return list;

    }

    public ProductDetailsBeanList getResponseBean(Product_Master p) {
        return ProductDetailsBeanList.builder()
                .name(p.getName())
                .productMasterId(p.getId())
                .mrp(CommonUtils.paiseToRupee(p.getMrp()))
                .quantity(0L)
                .image(p.getCdn_url())
                .categoryId(p.getCatagory_id())
                .groupId(p.getGroup_id())
                .secure(false)
                .build();
    }

    public ProductDetailsBeanList getResponseBean(Products p, Map<String, Long> highestPrize) {
        return ProductDetailsBeanList.builder()
                .name(p.getName())
                .id(p.getId())
                .mrp(CommonUtils.paiseToRupee(p.getMrp()))
                .sellingPrice(CommonUtils.paiseToRupee(highestPrize.getOrDefault(p.getId(), 0L)))
                .quantity(p.getQuantity())
                .image(CollectionUtils.isEmpty(p.getMedia()) ? "" : p.getMedia().stream().filter(e -> e.getOrder() == 0).findFirst().get().getCdn_url())
                .categoryId(p.getCategory_id())
                .groupId(p.getGroup_id())
                .secure(p.getIs_secure())
                .search_sub_title(p.getSelected_sub_catagories().get(0).getSelected_attributes().get(0))
                .productMasterId(p.getProduct_master_id())
                .build();
    }

    private void makeValuesUnique(Map<String, List<String>> map) {
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            List<String> uniqueList = new ArrayList<>(new HashSet<>(entry.getValue()));
            entry.setValue(uniqueList);
        }
    }
}
