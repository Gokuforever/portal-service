package com.sorted.portal.bl_services;

import com.sorted.commons.beans.GroupComponent;
import com.sorted.commons.beans.ProductCarousel;
import com.sorted.commons.beans.SelectedSubCategories;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.AssetType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.repository.mongo.ProductRepository;
import com.sorted.commons.utils.ComboUtility;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.assisting.beans.config.*;
import com.sorted.portal.request.beans.MetaDataReq;
import com.sorted.portal.response.beans.Config;
import com.sorted.portal.response.beans.MetaData;
import com.sorted.portal.service.CategoryFilterService;
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

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageMetaData_BLService {

    private final Category_MasterService categoryMasterService;
    private final Users_Service usersService;
    private final Product_Master_Service productMasterService;
    private final ProductRepository productRepository;
    private final HomeConfigService homeConfigService;
    private final CategoryFilterService categoryFilterService;
    private final AssetsService assetsService;
    private final ComboService comboService;
    private final ComboUtility comboUtility;

    // Cache for /preferences response
    private volatile Config preferencesCache = null;
    private volatile long preferencesCacheTime = 0;
    private static final long CACHE_TTL_MS = 3600000; // 1 hour TTL

    // Cache for /getMetaData response
    private volatile SEResponse metaDataCache = null;
    private volatile long metaDataCacheTime = 0;


    @PostMapping("/cache/clear")
    public void clearCache() {
        preferencesCache = null;
        preferencesCacheTime = 0;
        metaDataCache = null;
        metaDataCacheTime = 0;
        log.info("All caches cleared successfully");
    }

    @GetMapping("/preferences")
    public Config getPreferences() {
        log.info("getPreferences:: API started");

        // Check if cache is valid
        if (preferencesCache != null && (System.currentTimeMillis() - preferencesCacheTime) < CACHE_TTL_MS) {
            log.info("Returning cached preferences response");
            return preferencesCache;
        }

        List<Category_Master> categoryMasterData = categoryFilterService.getFilters();
//        List<Category_Master> categoryMasterData = this.getCategoryMasterData();

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
            filter.addClause(WhereClause.isNotEmpty("media.cdn_url"));
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
                filterPM.addClause(WhereClause.isNotEmpty("media.cdn_url"));
                filterPM.addClause(WhereClause.eq(Products.Fields.seller_id, "68711a63a2dcdf55ed170972"));
                if (group.getFilters() != null && !group.getFilters().isEmpty()) {
                    for (Map.Entry<String, List<String>> entry : group.getFilters().entrySet()) {
                        if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                            Map<String, Object> map = new HashMap<>();
                            map.put(SelectedSubCategories.Fields.sub_category, entry.getKey());
                            map.put(SelectedSubCategories.Fields.selected_attributes, entry.getValue());
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
                    .combo(false)
                    .build();
            homeProductsBeans.add(homeProductsBean);
        }

        List<PromoBanners> promoBanners = new ArrayList<>();

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(AssetsEntity.Fields.type, AssetType.HOME_PROMO_BANNER.name()));

        List<AssetsEntity> assetsEntities = assetsService.repoFind(filter);
        if (!CollectionUtils.isEmpty(assetsEntities)) {
            for (AssetsEntity assetsEntity : assetsEntities) {
                promoBanners.add(PromoBanners.builder()
                        .url(assetsEntity.getUrl())
                        .order(assetsEntity.getOrder())
                        .altText(assetsEntity.getAltText())
                        .mobileView(assetsEntity.isMobileView())
                        .build());
            }
        }

        List<ProductBean> comboBeans = new ArrayList<>();

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Combo> combos = comboService.repoFind(filterC);
        if (!CollectionUtils.isEmpty(combos)) {
            Combo combo = combos.get(0);
            boolean valid = comboUtility.validateCombo(combo);
            if (valid) {
                List<Products> products = comboUtility.getProductsByCombo(combo);
                long averageQuantity = products.stream().map(Products::getQuantity).toList().stream().sorted().toList().get(0);
                ProductBean productBean = ProductBean.builder()
                        .id(combo.getId())
                        .name(combo.getName())
                        .secure(false)
                        .image(!CollectionUtils.isEmpty(combo.getMedia()) && !combo.getMedia().isEmpty() ? combo.getMedia().get(0).getCdn_url() : "")
                        .mrp(CommonUtils.paiseToRupee(combo.getMrp()))
                        .sellingPrice(CommonUtils.paiseToRupee(combo.getSelling_price()))
                        .quantity(averageQuantity)
                        .build();
                comboBeans.add(productBean);
            }


        }

        Assets assets = Assets.builder()
                .homePromoBanners(promoBanners)
                .build();

        Config config = Config.builder()
                .categories(categoryMasterData)
                .homeProducts(homeProductsBeans)
                .assets(assets)
                .build();

        // Update cache
        preferencesCache = config;
        preferencesCacheTime = System.currentTimeMillis();
        log.info("Preferences response cached");

        return config;
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
                    .quantity(product.getQuantity())
                    .secure(Boolean.TRUE.equals(product.getIs_secure()))
                    .build();
            productBeans.add(productBean);
        }
        return productBeans;
    }

    @PostMapping("/getMetaData")
    public SEResponse getMetaData(@RequestBody SERequest request) {

        log.info("getMetaData:: API started");

        // Check if cache is valid
        if (metaDataCache != null && (System.currentTimeMillis() - metaDataCacheTime) < CACHE_TTL_MS) {
            log.info("Returning cached metadata response");
            return metaDataCache;
        }

        MetaDataReq req = request.getGenericRequestDataObject(MetaDataReq.class);
        List<String> ids = req.getIds();
        MetaData data = new MetaData();

        List<Category_Master> categoryMasterData = this.getCategoryMasterData();
        if (!CollectionUtils.isEmpty(categoryMasterData)) {
            data.setCatagories(categoryMasterData);
        }

        List<Product_Master> listPM = this.getProductMasters();
        if (!CollectionUtils.isEmpty(listPM)) {
            data.setProducts(listPM);
        }
        data.setUpdated_at(LocalDateTime.now());

        log.info("getMetaData:: API ended");
        SEResponse response = SEResponse.getBasicSuccessResponseObject(data, ResponseCode.SUCCESSFUL);

        // Update cache
        metaDataCache = response;
        metaDataCacheTime = System.currentTimeMillis();
        log.info("Metadata response cached");

        return response;
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
