package com.sorted.common.service;

import com.sorted.common.beans.*;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.*;
import com.sorted.common.enums.AssetType;
import com.sorted.common.enums.NotifyRestockStatus;
import com.sorted.common.exceptions.DeliveryNotAvailableException;
import com.sorted.common.helper.AggregationFilter.*;
import com.sorted.common.helper.Pagination;
import com.sorted.common.repository.mongo.ProductRepository;
import com.sorted.common.utils.ComboUtility;
import com.sorted.common.utils.CommonUtils;
import com.sorted.common.utils.ProductUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreferencesHandlerService {

    private final ZoneHandlerService zoneHandlerService;
    private final Product_Master_Service productMasterService;
    private final HomeConfigService homeConfigService;
    private final ProductService productService;
    private final AssetsService assetsService;
    private final ComboService comboService;
    private final ComboUtility comboUtility;
    private final ProductRepository productRepository;
    private final CategoryFilterServiceV2 categoryFilterService;
    private final Users_Service usersService;
    private final ProductUtility productUtility;
    private final NotifyRestockService notifyRestockService;

    public Config fetchPreference(double lat, double lng, Users users) {
        Seller seller;
        try {
            ZoneEntity zoneEntity = zoneHandlerService.identifyZone(lat, lng);

            users.setNearestZoneId(zoneEntity.getZoneId());
            users.setCurrentLat(BigDecimal.valueOf(lat));
            users.setCurrentLng(BigDecimal.valueOf(lng));
            usersService.update(users.getId(), users, Defaults.SYSTEM_ADMIN);

            seller = zoneHandlerService.getSellerByZone(zoneEntity.getZoneId(), lat, lng);
        } catch (DeliveryNotAvailableException e) {
            return Config.builder()
                    .isLocationServiceable(false)
                    .build();
        }

        SEFilter filter2 = new SEFilter(SEFilterType.AND);
        filter2.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter2.addClause(WhereClause.eq(Products.Fields.seller_id, seller.getId()));
        List<Products> products = productService.repoFind(filter2);

        // Check for duplicate product_master_id and create map (keeping first occurrence if duplicates exist)
        Map<String, Products> productsMapBySeller = products.stream()
                .collect(Collectors.toMap(
                        Products::getProduct_master_id,
                        p -> p,
                        (existing, replacement) -> {
                            return existing; // Keep the first occurrence
                        }
                ));
        Set<String> productIdsBySeller = productsMapBySeller.keySet();

        HomeProductsBean.HomeProductsBeanBuilder homeProductsBeanBuilder = HomeProductsBean.builder();

        List<HomeConfig> homeConfigs = homeConfigService.repoFindAll();

        List<HomeProductsBean> homeProductsBeans = new ArrayList<>();

        SEFilter filterRN = new SEFilter(SEFilterType.AND);
        filterRN.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterRN.addClause(WhereClause.eq(NotifyRestockEntity.Fields.userId, users.getId()));
        filterRN.addClause(WhereClause.eq(NotifyRestockEntity.Fields.status, NotifyRestockStatus.PENDING.name()));

        List<NotifyRestockEntity> notifyRestockEntities = notifyRestockService.repoFind(filterRN);
        List<String> restockNotificationsEnabledProducts = new ArrayList<>();
        if (!CollectionUtils.isEmpty(notifyRestockEntities)) {
            restockNotificationsEnabledProducts = notifyRestockEntities.stream().map(NotifyRestockEntity::getProductMasterId).toList();
        }

        if (!CollectionUtils.isEmpty(productIdsBySeller)) {
            Map<String, Long> highestPrize = productUtility.getProductHighestSellingPrice(productIdsBySeller.toArray(new String[0]));
            for (HomeConfig homeConfig : homeConfigs) {

                String categoryId = homeConfig.getCategoryId();
                homeProductsBeanBuilder.mainBadge(homeConfig.getMainBadge())
                        .mainTitle(homeConfig.getMainTitle())
                        .mainSubtitle(homeConfig.getMainSubtitle())
                        .categoryId(categoryId);

                SEFilter filter3 = new SEFilter(SEFilterType.AND);
                filter3.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                filter3.addClause(WhereClause.eq(Products.Fields.category_id, categoryId));
                filter3.addClause(WhereClause.isNotNull("media.cdn_url"));
                filter3.addClause(WhereClause.eq(Products.Fields.seller_id, seller.getId()));

                filter3.setOrderBy(new OrderBy(Products.Fields.quantity, SortOrder.DESC));

                List<Products> randomProducts = productRepository.getRandomProducts(filter3, 7);

                ProductCarousel productCarousel = homeConfig.getProductCarousel();
                List<ProductBean> randomProductBeans = new ArrayList<>();
                for (Products randomProduct : randomProducts) {
                    randomProductBeans.add(getProductBean(randomProduct, highestPrize, restockNotificationsEnabledProducts.contains(randomProduct.getProduct_master_id())));
                }

                if (CollectionUtils.isEmpty(randomProducts) || randomProducts.size() < 7) {
                    SEFilter filter = new SEFilter(SEFilterType.AND);
                    filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                    filter.addClause(WhereClause.eq(Product_Master.Fields.catagory_id, categoryId));
                    filter.addClause(WhereClause.isNotNull(Product_Master.Fields.cdn_url));
                    if (!CollectionUtils.isEmpty(randomProducts)) {
                        filter.addClause(WhereClause.nin(BaseMongoEntity.Fields.id, randomProducts.stream().map(Products::getProduct_master_id).toList()));
                    }
                    Pagination pagination;
                    if (CollectionUtils.isEmpty(randomProducts)) {
                        pagination = new Pagination(0, 7);
                    } else {
                        pagination = new Pagination(0, 7 - randomProducts.size());
                    }
                    filter.setPagination(pagination);

                    List<Product_Master> productMasters = productMasterService.repoFind(filter);
                    for (Product_Master productMaster : productMasters) {
                        randomProductBeans.add(getProductBean(productMaster, restockNotificationsEnabledProducts.contains(productMaster.getId())));
                    }
                }

                ProductCarouselBean productCarouselBean = ProductCarouselBean.builder()
                        .title(productCarousel.getTitle())
                        .subtitle(productCarousel.getSubtitle())
                        .products(randomProductBeans)
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
                    filterPM.addClause(WhereClause.eq(Products.Fields.seller_id, seller.getId()));
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

                    filterPM.setOrderBy(new OrderBy(Products.Fields.quantity, SortOrder.DESC));

                    List<Products> productsByGroup = productRepository.getRandomProducts(filterPM, 7);

                    List<ProductBean> productListByGroup = new ArrayList<>();

                    for (Products productByGroup : productsByGroup) {
                        productListByGroup.add(getProductBean(productByGroup, highestPrize, restockNotificationsEnabledProducts.contains(productByGroup.getProduct_master_id())));
                    }

                    if (CollectionUtils.isEmpty(productsByGroup) || productsByGroup.size() < 7) {
                        SEFilter filterMaster = new SEFilter(SEFilterType.AND);
                        filterMaster.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                        filterMaster.addClause(WhereClause.eq(Product_Master.Fields.catagory_id, categoryId));
                        filterMaster.addClause(WhereClause.eq(Product_Master.Fields.group_id, group.getId()));
                        filterMaster.addClause(WhereClause.isNotNull(Product_Master.Fields.cdn_url));
                        if (!CollectionUtils.isEmpty(productsByGroup)) {
                            filterMaster.addClause(WhereClause.nin(BaseMongoEntity.Fields.id, productsByGroup.stream().map(Products::getProduct_master_id).toList()));
                        }
                        if (group.getFilters() != null && !group.getFilters().isEmpty()) {
                            for (Map.Entry<String, List<String>> entry : group.getFilters().entrySet()) {
                                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                                    filterMaster.addClause(WhereClause.in("sub_categories." + entry.getKey(), entry.getValue()));
                                }
                            }
                        }

                        Pagination pagination;
                        if (CollectionUtils.isEmpty(productsByGroup)) {
                            pagination = new Pagination(0, 7);
                        } else {
                            pagination = new Pagination(0, 7 - productsByGroup.size());
                        }
                        filterMaster.setPagination(pagination);
                        List<Product_Master> productMasters = productMasterService.repoFind(filterMaster);
                        if (!CollectionUtils.isEmpty(productMasters)) {
                            for (Product_Master productMaster : productMasters) {
                                productListByGroup.add(getProductBean(productMaster, restockNotificationsEnabledProducts.contains(productMaster.getId())));
                            }
                        }
                    }

                    GroupComponentBean groupComponentBean = GroupComponentBean.builder()
                            .groupId(group.getId())
                            .title(group.getTitle())
                            .filters(group.getFilters())
                            .products(productListByGroup)
                            .build();
                    groupComponentBeans.add(groupComponentBean);
                }
                HomeProductsBean homeProductsBean = homeProductsBeanBuilder.groupComponent(groupComponentBeans)
                        .combo(false)
                        .build();
                homeProductsBeans.add(homeProductsBean);
            }
        }

        List<PromoBanners> promoBanners = new ArrayList<>();

        SEFilter filter4 = new SEFilter(SEFilterType.AND);
        filter4.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter4.addClause(WhereClause.eq(AssetsEntity.Fields.type, AssetType.HOME_PROMO_BANNER.name()));

        List<AssetsEntity> assetsEntities = assetsService.repoFind(filter4);
        if (!CollectionUtils.isEmpty(assetsEntities)) {
            List<AssetsEntity> entities = assetsEntities.stream().sorted(Comparator.comparing(AssetsEntity::getOrder)).toList();
            for (AssetsEntity assetsEntity : entities) {
                promoBanners.add(PromoBanners.builder()
                        .url(assetsEntity.getUrl())
                        .order(assetsEntity.getOrder())
                        .altText(assetsEntity.getAltText())
                        .mobileView(assetsEntity.isMobileView())
                        .build());
            }
        }

//        List<ProductBean> comboBeans = new ArrayList<>();
//
//        SEFilter filterC = new SEFilter(SEFilterType.AND);
//        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//
//        List<Combo> combos = comboService.repoFind(filterC);
//        if (!CollectionUtils.isEmpty(combos)) {
//            Combo combo = combos.get(0);
//            boolean valid = comboUtility.validateCombo(combo);
//            if (valid) {
//                List<Products> comboProducts = comboUtility.getProductsByCombo(combo);
//                long averageQuantity = products.stream().map(Products::getQuantity).toList().stream().sorted().toList().get(0);
//                ProductBean productBean = ProductBean.builder()
//                        .id(combo.getId())
//                        .name(combo.getName())
//                        .secure(false)
//                        .image(!CollectionUtils.isEmpty(combo.getMedia()) ? combo.getMedia().get(0).getCdn_url() : comboProducts.stream().anyMatch(p -> !CollectionUtils.isEmpty(p.getMedia())) ? comboProducts.stream().filter(p -> !CollectionUtils.isEmpty(p.getMedia())).findFirst().get().getMedia().get(0).getCdn_url() : "")
//                        .mrp(CommonUtils.paiseToRupee(combo.getMrp()))
//                        .sellingPrice(CommonUtils.paiseToRupee(combo.getSelling_price()))
//                        .quantity(averageQuantity)
//                        .build();
//                comboBeans.add(productBean);
//            }
////            HomeProductsBean homeProductsBean = HomeProductsBean.builder()
////                    .combo(true)
////                    .mainBadge("Best Seller Combo")
////                    .mainTitle("Engineering Starter Pack")
////                    .mainSubtitle("Get all essentials in one bundle")
////                    .productCarousel(ProductCarouselBean.builder()
////                            .title("Included in this Combo")
////                            .subtitle("Handpicked books to kickstart your semester")
////                            .products(comboBeans)
////                            .build())
////                    .build();
////            homeProductsBeans.add(homeProductsBean);
//        }

        Assets assets = Assets.builder()
                .homePromoBanners(promoBanners)
                .build();

        List<Category_Master> categoryMasterData = categoryFilterService.getFilters();

        return Config.builder()
                .categories(categoryMasterData)
                .homeProducts(homeProductsBeans)
                .assets(assets)
                .isLocationServiceable(true)
                .build();


    }

    private ProductBean getProductBean(Products product, Map<String, Long> highestPrize, boolean restockNotificationEnabled) {
        return ProductBean.builder()
                .mrp(CommonUtils.paiseToRupee(product.getMrp()))
                .sellingPrice(CommonUtils.paiseToRupee(highestPrize.get(product.getProduct_master_id())))
                .image(CollectionUtils.isEmpty(product.getMedia()) ? "" : product.getMedia().stream().filter(e -> e.getOrder() == 0).findFirst().get().getCdn_url())
                .id(product.getId())
                .name(product.getName())
                .quantity(product.getQuantity())
                .secure(product.getIs_secure())
                .productMasterId(product.getProduct_master_id())
                .isRestockNotificationEnabled(restockNotificationEnabled)
                .build();
    }

    private ProductBean getProductBean(Product_Master product, boolean restockNotificationEnabled) {
        return ProductBean.builder()
                .mrp(CommonUtils.paiseToRupee(product.getMrp()))
                .sellingPrice(CommonUtils.paiseToRupee(product.getMrp()))
                .image(product.getCdn_url())
                .id(product.getId())
                .productMasterId(product.getId())
                .name(product.getName())
                .quantity(0L)
                .secure(false)
                .isRestockNotificationEnabled(restockNotificationEnabled)
                .build();
    }

}
