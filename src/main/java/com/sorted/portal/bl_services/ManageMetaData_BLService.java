package com.sorted.portal.bl_services;

import com.sorted.commons.beans.GroupComponent;
import com.sorted.commons.beans.ProductCarousel;
import com.sorted.commons.beans.SelectedSubCatagories;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.Product_Master_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.repository.mongo.ProductRepository;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.assisting.beans.config.GroupComponentBean;
import com.sorted.portal.assisting.beans.config.HomeProductsBean;
import com.sorted.portal.assisting.beans.config.ProductBean;
import com.sorted.portal.assisting.beans.config.ProductCarouselBean;
import com.sorted.portal.request.beans.MetaDataReq;
import com.sorted.portal.response.beans.Config;
import com.sorted.portal.response.beans.MetaData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageMetaData_BLService {

    private final Category_MasterService categoryMasterService;
    private final Users_Service usersService;
    private final Product_Master_Service productMasterService;
    private final ProductRepository productRepository;
    private final HomeConfigService homeConfigService;

    private final Map<String, List<Category_Master>> categoryCache = new ConcurrentHashMap<>();
    private final Map<String, List<Product_Master>> productCache = new ConcurrentHashMap<>();
    private static final String CM_CACHE_KEY = "CM_DATA";
    private static final String PM_CACHE_KEY = "PM_DATA";


    @PostMapping("/cache/clear")
    public void clearCache() {
        categoryCache.clear();
        productCache.clear();
    }

    @GetMapping("/preferences")
    public Config getPreferences() {
        log.info("getPreferences:: API started");
        List<Category_Master> categoryMasterData = this.getCategoryMasterData();

        HomeProductsBean.HomeProductsBeanBuilder homeProductsBeanBuilder = HomeProductsBean.builder();

        List<HomeConfig> homeConfigs = homeConfigService.repoFindAll();

        List<HomeProductsBean> homeProductsBeans = new ArrayList<>();

        for (HomeConfig homeConfig : homeConfigs) {

            String categoryId = homeConfig.getCategoryId();
            homeProductsBeanBuilder.mainBadge(homeConfig.getMainBadge())
                    .mainTitle(homeConfig.getMainTitle())
                    .mainSubtitle(homeConfig.getMainSubtitle())
                    .categoryId(categoryId);

            SEFilter filter = new SEFilter(SEFilterType.AND);
            filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filter.addClause(WhereClause.eq(Products.Fields.category_id, categoryId));
            filter.addClause(WhereClause.isNotNull("media.cdn_url"));
            filter.addClause(WhereClause.eq(Products.Fields.seller_id, "68711a63a2dcdf55ed170972"));

            List<Products> randomProducts = productRepository.getRandomProducts(filter, 7);

            ProductCarousel productCarousel = homeConfig.getProductCarousel();

            List<ProductBean> productBeans = getProductBeans(randomProducts);


            ProductCarouselBean productCarouselBean = ProductCarouselBean.builder()
                    .title(productCarousel.getTitle())
                    .subtitle(productCarousel.getSubtitle())
                    .products(productBeans)
                    .build();

            homeProductsBeanBuilder.productCarousel(productCarouselBean);

            List<GroupComponent> groupComponent = homeConfig.getGroupComponent();

            List<GroupComponentBean> groupComponentBeans = new ArrayList<>();

            for (GroupComponent group : groupComponent) {
                SEFilter filterPM = new SEFilter(SEFilterType.AND);
                filterPM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                filterPM.addClause(WhereClause.eq(Products.Fields.category_id, categoryId));
                filterPM.addClause(WhereClause.eq(Products.Fields.group_id, group.getId()));
                filterPM.addClause(WhereClause.isNotNull("media.cdn_url"));
                filterPM.addClause(WhereClause.eq(Products.Fields.seller_id, "68711a63a2dcdf55ed170972"));
                if (group.getFilters() != null && !group.getFilters().isEmpty()) {
                    for (Map.Entry<String, List<String>> entry : group.getFilters().entrySet()) {
                        if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                            Map<String, Object> map = new HashMap<>();
                            map.put(SelectedSubCatagories.Fields.sub_category, entry.getKey());
                            map.put(SelectedSubCatagories.Fields.selected_attributes, entry.getValue());
                            filterPM.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
                        }
                    }
                }

                List<Products> products = productRepository.getRandomProducts(filterPM, 7);

                GroupComponentBean groupComponentBean = GroupComponentBean.builder()
                        .groupId(group.getId())
                        .title(group.getTitle())
                        .filters(group.getFilters())
                        .products(getProductBeans(products))
                        .build();
                groupComponentBeans.add(groupComponentBean);
            }
            HomeProductsBean homeProductsBean = homeProductsBeanBuilder.groupComponent(groupComponentBeans)
                    .build();
            homeProductsBeans.add(homeProductsBean);
        }


        return Config.builder()
                .categories(categoryMasterData)
                .homeProducts(homeProductsBeans)
                .build();
    }

    @NotNull
    private static List<ProductBean> getProductBeans(List<Products> randomProducts) {
        List<ProductBean> productBeans = new ArrayList<>();
        for (Products product : randomProducts) {

            ProductBean productBean = ProductBean.builder()
                    .mrp(CommonUtils.paiseToRupee(product.getMrp()))
                    .sellingPrice(CommonUtils.paiseToRupee(product.getSelling_price()))
                    .image(CollectionUtils.isEmpty(product.getMedia()) ? "" : product.getMedia().stream().filter(e -> e.getOrder() == 0).findFirst().get().getCdn_url())
                    .id(product.getId())
                    .name(product.getName())
                    .build();
            productBeans.add(productBean);
        }
        return productBeans;
    }

    @PostMapping("/getMetaData")
    public SEResponse getMetaData(@RequestBody SERequest request) {

        log.info("getMetaData:: API started");
        MetaDataReq req = request.getGenericRequestDataObject(MetaDataReq.class);
        List<String> ids = req.getIds();
        MetaData data = new MetaData();

        List<Category_Master> categoryMasterData = this.getCategoryMasterData();
//        List<Category_Master> categoryMasterData = categoryCache.computeIfAbsent(CM_CACHE_KEY, key -> this.getCategoryMasterData());
        if (!CollectionUtils.isEmpty(categoryMasterData)) {
            data.setCatagories(categoryMasterData);
        }

        List<Product_Master> listPM = this.getProductMasters();
        if (!CollectionUtils.isEmpty(listPM)) {
            data.setProducts(listPM);
        }
        data.setUpdated_at(LocalDateTime.now());

        log.info("getMetaData:: API ended");
        return SEResponse.getBasicSuccessResponseObject(data, ResponseCode.SUCCESSFUL);
    }

    private List<Product_Master> getProductMasters() {
        SEFilter filterPM = new SEFilter(SEFilterType.AND);
        filterPM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterPM.setOrderBy(new AggregationFilter.OrderBy(BaseMongoEntity.Fields.id, AggregationFilter.SortOrder.DESC));
        filterPM.addProjection(Product_Master.Fields.name, Product_Master.Fields.catagory_id, Product_Master.Fields.sub_categories, Product_Master.Fields.cdn_url, Product_Master.Fields.group_id);
        return productMasterService.repoFind(filterPM);
    }

    private List<Category_Master> getCategoryMasterData() {
        SEFilter filterCM = new SEFilter(SEFilterType.AND);
        filterCM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterCM.addProjection(Category_Master.Fields.name, Category_Master.Fields.groups,
                Category_Master.Fields.category_code);
        return categoryMasterService.repoFind(filterCM);
    }

    @PostMapping("/getUserInfo")
    public SEResponse getUserInfo(HttpServletRequest httpServletRequest) {
        try {
            String req_user_id = httpServletRequest.getHeader("req_user_id");
            UsersBean usersBean = usersService.validateAndGetUserInfo(req_user_id);
            return SEResponse.getBasicSuccessResponseObject(usersBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/signup/verify:: exception occurred", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }
}
