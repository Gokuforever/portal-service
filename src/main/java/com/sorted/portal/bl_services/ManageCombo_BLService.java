package com.sorted.portal.bl_services;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Combo;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.service.ComboService;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.CreateComboBean;
import com.sorted.portal.request.beans.GetCombosBean;
import com.sorted.portal.response.beans.ComboBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequestMapping("/combo")
@RequiredArgsConstructor
public class ManageCombo_BLService {

    private final Users_Service usersService;
    private final ProductService productService;
    private final ComboService comboService;

    @PostMapping("/create")
    public SEResponse create(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("Starting combo creation process");

        try {
            CreateComboBean req = request.getGenericRequestDataObject(CreateComboBean.class);
            log.debug("Extracted CreateComboBean from request: {}", req);

            CommonUtils.extractHeaders(httpServletRequest, req);
            log.debug("Headers extracted successfully");

            log.info("Validating user for combo creation activity");
            UsersBean usersBean = usersService.validateUserForActivity(req, Permission.EDIT, Activity.MANAGE_COMBO);
            log.debug("User validation successful for user: {}", usersBean.getId());

            Role role = usersBean.getRole();
            UserType userType = role.getUser_type();
            log.debug("User type: {}", userType);

            if (userType != UserType.SELLER) {
                log.warn("Access denied - user type {} is not SELLER", userType);
                throw new AccessDeniedException();
            }

            log.info("Validating combo creation request parameters");
            Preconditions.check(StringUtils.hasText(req.getName()), ResponseCode.MANDATE_COMBO_NAME);
            log.debug("Combo name validation passed: {}", req.getName());

            Preconditions.check(StringUtils.hasText(req.getDescription()), ResponseCode.MANDATE_COMBO_DESCRIPTION);
            log.debug("Combo description validation passed");

            Preconditions.check(CollectionUtils.isNotEmpty(req.getItem_ids()), ResponseCode.MANDATE_COMBO_PRODUCTS);
            log.debug("Combo items validation passed, item count: {}", req.getItem_ids().size());

            Preconditions.check(req.getPrice() != null && req.getPrice() > 0, ResponseCode.MANDATE_COMBO_PRICE);
            log.debug("Combo price validation passed: {}", req.getPrice());

            HashSet<String> itemIds = new HashSet<>(req.getItem_ids());
            itemIds.remove(null);
            log.debug("Cleaned item IDs, final count: {}", itemIds.size());

            log.info("Building filter to fetch products for seller: {}", usersBean.getSeller().getId());
            SEFilter filter = new SEFilter(SEFilterType.AND);
            filter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(itemIds)));
            filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filter.addClause(WhereClause.eq(Products.Fields.seller_id, usersBean.getSeller().getId()));

            log.info("Fetching products from database");
            List<Products> products = productService.repoFind(filter);
            log.debug("Retrieved {} products from database", products != null ? products.size() : 0);

            Preconditions.check(CollectionUtils.isNotEmpty(products), ResponseCode.MISSING_PRODUCTS);
            Preconditions.check(itemIds.size() == products.size(), ResponseCode.INVALID_PRODUCT_IDS);
            log.info("Product validation successful - all {} products found and valid", products.size());

            log.info("Creating new combo entity");
            Combo combo = new Combo();
            combo.setName(req.getName());
            combo.setDescription(req.getDescription());
            combo.setPrice(req.getPrice());
            combo.setSeller_id(usersBean.getSeller().getId());
            combo.setItem_ids(req.getItem_ids());
            combo.setActive(true);
            log.debug("Combo entity prepared: name={}, price={}, seller_id={}",
                    combo.getName(), combo.getPrice(), combo.getSeller_id());

            log.info("Saving combo to database");
            comboService.create(combo, usersBean.getId());
            log.info("Combo created successfully with name: {}", combo.getName());

            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (AccessDeniedException e) {
            log.error("Access denied during combo creation", e);
            throw e;
        } catch (Exception e) {
            log.error("Error occurred during combo creation", e);
            throw e;
        }
    }

    public SEResponse getCombos(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {

        GetCombosBean req = request.getGenericRequestDataObject(GetCombosBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        UsersBean usersBean = usersService.validateUserForActivity(req, Permission.VIEW, Activity.PRODUCTS, Activity.INVENTORY_MANAGEMENT, Activity.MANAGE_COMBO);
        UserType userType = usersBean.getRole().getUser_type();
        String sellerId = switch (userType) {
            case CUSTOMER, GUEST -> usersBean.getNearestSeller();
            case SELLER -> usersBean.getRole().getSeller_id();
            default -> throw new AccessDeniedException();
        };

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Combo.Fields.seller_id, sellerId));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Combo> combos = comboService.repoFind(filter);
        Preconditions.check(CollectionUtils.isNotEmpty(combos), ResponseCode.NO_RECORD);

        Set<String> productIds = combos.stream().flatMap(e -> e.getItem_ids().stream()).collect(Collectors.toSet());

        SEFilter productFilter = new SEFilter(SEFilterType.AND);
        productFilter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(productIds)));
        productFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Products> products = productService.repoFind(productFilter);
        Preconditions.check(CollectionUtils.isNotEmpty(products), ResponseCode.NO_RECORD);

        Map<String, Products> productMap = products.stream().collect(Collectors.toMap(Products::getId, Function.identity()));

        List<ComboBean> comboBeans = new ArrayList<>();
        for (Combo combo : combos) {


            comboBeans.add(ComboBean.builder()
                    .name(combo.getName())
                    .description(combo.getDescription())
                    .price(CommonUtils.paiseToRupee(combo.getPrice()))
                    .creationDate(combo.getCreation_date_str())
                    .id(combo.getId())
                    .build());
        }

        return SEResponse.getBasicSuccessResponseList(comboBeans, ResponseCode.SUCCESSFUL);
    }

}