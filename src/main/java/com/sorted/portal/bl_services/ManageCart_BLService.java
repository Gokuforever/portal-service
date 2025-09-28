package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.*;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.mongo.CouponEntity;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.entity.service.CouponService;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CartUtility;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.CouponUtility;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.assisting.beans.CartItemsBean;
import com.sorted.portal.request.beans.ApplyCouponBean;
import com.sorted.portal.request.beans.CartCRUDBean;
import com.sorted.portal.request.beans.CartFetchReqBean;
import com.sorted.portal.response.beans.FetchCartV2;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageCart_BLService {

    private final Cart_Service cart_Service;
    private final ProductService productService;
    private final Users_Service users_Service;
    private final CouponService couponService;
    private final CouponUtility couponUtility;
    private final CartUtility cartUtility;

    @Value("${se.minimum-cart-value.in-paise:10000}")
    private long minCartValueInPaise;

    @Value("${se.fixed-delivery-charge.in-paise:4000}")
    private long fixedDeliveryFee;

    @Value("${se.small-cart-fee.in-paise:1000}")
    private long fixedSmallCartFee;

    @Value("${se.handling-fee.in-paise:900}")
    private long fixedHandlingFee;

    @GetMapping("/v2/cart/fetch")
    public FetchCartV2 v2fetch(HttpServletRequest httpServletRequest) throws JsonProcessingException {
        String req_user_id = httpServletRequest.getHeader("req_user_id");
        if (!StringUtils.hasText(req_user_id)) {
            throw new AccessDeniedException();
        }

        // Use more efficient query with projection to get only needed fields
        SEFilter cartFilter = new SEFilter(SEFilterType.AND);
        cartFilter.addClause(WhereClause.eq(Cart.Fields.user_id, req_user_id));
        cartFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(cartFilter);
        if (cart == null) {
            cart = new Cart();
            cart.setUser_id(req_user_id);
            cart_Service.create(cart, req_user_id);

            // Return early for empty cart to avoid unnecessary processing
            return FetchCartV2.builder()
                    .totalCount(0L)
                    .totalAmount(BigDecimal.ZERO)
                    .freeDeliveryDiff(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .deliveryFree(false)
                    .savings(BigDecimal.ZERO)
                    .minimumCartValue(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .build();
        }

        List<Item> cartItems = cart.getCart_items();
        if (CollectionUtils.isEmpty(cartItems)) {
            // Return early for empty cart
            return FetchCartV2.builder()
                    .totalCount(0L)
                    .totalAmount(BigDecimal.ZERO)
                    .freeDeliveryDiff(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .deliveryFree(false)
                    .savings(BigDecimal.ZERO)
                    .minimumCartValue(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .build();
        }

        CartBeanV2 cartBean = cartUtility.getCartBeanV2(cart);
        BillingSummary billingSummary = cartBean.getBillingSummary();

        BigDecimal itemCost = billingSummary.getToPay();
        BigDecimal freeDeliveryDiff = BigDecimal.ZERO;
        if (!cartBean.isFreeDelivery()) {
            BigDecimal platformFee = CommonUtils.paiseToRupee(fixedDeliveryFee).add(CommonUtils.paiseToRupee(fixedHandlingFee)).add(CommonUtils.paiseToRupee(fixedSmallCartFee));
            itemCost = billingSummary.getToPay().subtract(platformFee);
            freeDeliveryDiff = CommonUtils.paiseToRupee(minCartValueInPaise).subtract(itemCost);
        }

        return FetchCartV2.builder()
                .totalCount(cartBean.getTotalItemCount()) // Use actual count of valid items
                .totalAmount(billingSummary.getToPay())
                .freeDeliveryDiff(freeDeliveryDiff)
                .deliveryFree(cartBean.isFreeDelivery())
                .savings(billingSummary.getSavings())
                .minimumCartValue(CommonUtils.paiseToRupee(minCartValueInPaise))
                .totalItemCost(itemCost)
                .build();
    }

    @GetMapping("/cart/v2/fetch")
    public FetchCartV2 fetchV2(HttpServletRequest httpServletRequest) throws JsonProcessingException {
        String req_user_id = httpServletRequest.getHeader("req_user_id");
        if (!StringUtils.hasText(req_user_id)) {
            throw new AccessDeniedException();
        }

        // Use more efficient query with projection to get only needed fields
        SEFilter cartFilter = new SEFilter(SEFilterType.AND);
        cartFilter.addClause(WhereClause.eq(Cart.Fields.user_id, req_user_id));
        cartFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(cartFilter);
        if (cart == null) {
            cart = new Cart();
            cart.setUser_id(req_user_id);
            cart_Service.create(cart, req_user_id);

            // Return early for empty cart to avoid unnecessary processing
            return FetchCartV2.builder()
                    .totalCount(0L)
                    .totalAmount(BigDecimal.ZERO)
                    .freeDeliveryDiff(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .deliveryFree(false)
                    .savings(BigDecimal.ZERO)
                    .minimumCartValue(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .build();
        }

        List<Item> cartItems = cart.getCart_items();
        if (CollectionUtils.isEmpty(cartItems)) {
            // Return early for empty cart
            return FetchCartV2.builder()
                    .totalCount(0L)
                    .totalAmount(BigDecimal.ZERO)
                    .freeDeliveryDiff(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .deliveryFree(false)
                    .savings(BigDecimal.ZERO)
                    .minimumCartValue(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .build();
        }

        CartBean cartBean = cartUtility.getCartBean(cart);

        BigDecimal freeDeliveryDiff = BigDecimal.ZERO;
        boolean freeDelivery = cartBean.is_free_delivery();
        if (!freeDelivery) {
            freeDeliveryDiff = CommonUtils.paiseToRupee(minCartValueInPaise).subtract(cartBean.getTotal_amount());
        }
        BigDecimal discountAmount = cartBean.getDiscountAmount();
        if (freeDelivery) {
            cartBean.setDiscountAmount(discountAmount.add(CommonUtils.paiseToRupee(fixedDeliveryFee)));
        }

        BigDecimal difference = cartBean.getItem_total_mrp().subtract(cartBean.getTotal_amount());


        return FetchCartV2.builder()
                .totalCount(cartBean.getTotal_count()) // Use actual count of valid items
                .totalAmount(cartBean.getTotal_amount())
                .freeDeliveryDiff(freeDeliveryDiff)
                .deliveryFree(freeDelivery)
                .savings(discountAmount.add(difference))
                .minimumCartValue(CommonUtils.paiseToRupee(minCartValueInPaise))
                .build();
    }

    @PostMapping("/cart/clear")
    public CartBeanV2 clear(HttpServletRequest httpServletRequest) {
        try {
            String req_user_id = httpServletRequest.getHeader("req_user_id");
            UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, Permission.VIEW,
                    Activity.CART_MANAGEMENT);
            switch (usersBean.getRole().getUser_type()) {
                case CUSTOMER, GUEST:
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            SEFilter filterC = new SEFilter(SEFilterType.AND);
            filterC.addClause(WhereClause.eq(Cart.Fields.user_id, req_user_id));
            filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Cart cart = cart_Service.repoFindOne(filterC);
            if (cart == null) {
                cart = new Cart();
                cart.setUser_id(req_user_id);
                cart_Service.create(cart, req_user_id);
            }
            cart.setCart_items(new ArrayList<>());
            cart_Service.update(cart.getId(), cart, req_user_id);

            return cartUtility.getCartBeanV2(cart);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/clear:: exception occurred");
            log.error("/clear:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/cart/fetch")
    public SEResponse fetch(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            CartFetchReqBean fetchReqBean = request.getGenericRequestDataObject(CartFetchReqBean.class);
            CommonUtils.extractHeaders(httpServletRequest, fetchReqBean);
            String address_id = StringUtils.hasText(fetchReqBean.getAddress_id()) ? fetchReqBean.getAddress_id() : null;
            UsersBean usersBean = users_Service.validateUserForActivity(fetchReqBean.getReq_user_id(), Permission.VIEW,
                    Activity.CART_MANAGEMENT);
            switch (usersBean.getRole().getUser_type()) {
                case CUSTOMER, GUEST:
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            SEFilter filterC = new SEFilter(SEFilterType.AND);
            filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
            filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Cart cart = cart_Service.repoFindOne(filterC);
            if (cart == null) {
                cart = new Cart();
                cart.setUser_id(usersBean.getId());
                cart = cart_Service.create(cart, usersBean.getId());
            }
            CartBean cartBean = cartUtility.getCartBean(cart, address_id, usersBean.getFirst_name() + " " + usersBean.getLast_name());
            return SEResponse.getBasicSuccessResponseObject(cartBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/fetch:: exception occurred");
            log.error("/fetch:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/cart/fetch/all")
    public CartBeanV2 fetchAll(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            CartFetchReqBean fetchReqBean = request.getGenericRequestDataObject(CartFetchReqBean.class);
            CommonUtils.extractHeaders(httpServletRequest, fetchReqBean);
            String address_id = StringUtils.hasText(fetchReqBean.getAddress_id()) ? fetchReqBean.getAddress_id() : null;
            UsersBean usersBean = users_Service.validateUserForActivity(fetchReqBean.getReq_user_id(), Permission.VIEW,
                    Activity.CART_MANAGEMENT);
            switch (usersBean.getRole().getUser_type()) {
                case CUSTOMER, GUEST:
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            SEFilter filterC = new SEFilter(SEFilterType.AND);
            filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
            filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Cart cart = cart_Service.repoFindOne(filterC);
            if (cart == null) {
                cart = new Cart();
                cart.setUser_id(usersBean.getId());
                cart = cart_Service.create(cart, usersBean.getId());
            }
            return cartUtility.getCartBeanV2(cart, address_id, usersBean.getFirst_name() + " " + usersBean.getLast_name());
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/fetch:: exception occurred");
            log.error("/fetch:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("v2/cart/add")
    public void addV2(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            CartCRUDBean req = request.getGenericRequestDataObject(CartCRUDBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.CART_MANAGEMENT);
            if (req.getItem() == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_ITEMS);
            }

            CartItemsBean itemBean = req.getItem();
            if (!StringUtils.hasText(itemBean.getProduct_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_ID);
            }
            if (itemBean.getQuantity() == null || itemBean.getQuantity() < 0) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_QUANTITY);
            }

            Cart cart = fetchUserCart(usersBean.getId());
            Products product = fetchProduct(itemBean.getProduct_id());

            List<Item> updatedCartItems;
            if (itemBean.getQuantity() <= 0) {
                // Remove item from cart
                updatedCartItems = removeCartItem(cart.getCart_items(), itemBean);
            } else {
                // Add or update item in cart
                updatedCartItems = addOrUpdateCartItem(cart.getCart_items(), itemBean, product);
            }

            cart.setCart_items(updatedCartItems);
            cart_Service.update(cart.getId(), cart, usersBean.getId());

        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/add/v2:: exception occurred", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    /**
     * Fetches the user's cart or throws if not found.
     */
    private Cart fetchUserCart(String userId) {
        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(Cart.Fields.user_id, userId));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        Cart cart = cart_Service.repoFindOne(filterC);
        if (cart == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }
        if (CollectionUtils.isEmpty(cart.getCart_items())) {
            cart.setCart_items(new ArrayList<>());
        }
        return cart;
    }

    /**
     * Fetches the product or throws if not found.
     */
    private Products fetchProduct(String productId) {
        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, productId));
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        Products product = productService.repoFindOne(filterP);
        if (product == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ITEM_NOT_FOUND);
        }
        return product;
    }

    /**
     * Removes the specified item (by productId and secure flag) from the cart.
     */
    private List<Item> removeCartItem(List<Item> cartItems, CartItemsBean itemBean) {
        if (CollectionUtils.isEmpty(cartItems)) return new ArrayList<>();
        return cartItems.stream()
                .filter(cartItem -> !cartItem.getProduct_id().equals(itemBean.getProduct_id()) || cartItem.is_secure() != itemBean.isSecure_item())
                .toList();
    }

    /**
     * Adds or updates the specified item in the cart, handling secure/non-secure logic.
     */
    private List<Item> addOrUpdateCartItem(List<Item> cartItems, CartItemsBean itemBean, Products product) {
        List<Item> updatedItems = new ArrayList<>();
        if (cartItems == null) cartItems = new ArrayList<>();
        String productId = itemBean.getProduct_id();
        boolean isSecure = itemBean.isSecure_item();

        Predicate<Item> sameProduct = x -> x.getProduct_id().equals(productId);
        Predicate<Item> sameSecure = x -> x.is_secure() == isSecure;
        Predicate<Item> diffSecure = x -> x.is_secure() != isSecure;

        Optional<Item> sameItemOpt = cartItems.stream().filter(sameProduct.and(sameSecure)).findFirst();
        Optional<Item> diffSecureOpt = cartItems.stream().filter(sameProduct.and(diffSecure)).findFirst();

        // Calculate new quantity
        long newQuantity = itemBean.getQuantity();
        if (sameItemOpt.isPresent()) {
            if (itemBean.isAdd()) {
                newQuantity += sameItemOpt.get().getQuantity();
            } else {
                newQuantity = sameItemOpt.get().getQuantity() - newQuantity;
            }
        }

        // If after update, quantity is zero or less, remove the item
        if (newQuantity <= 0) {
            return cartItems.stream()
                    .filter(cartItem -> !sameProduct.and(sameSecure).test(cartItem))
                    .toList();
        }

        // Calculate total_item for secure logic
        long total_item = newQuantity;
        if (diffSecureOpt.isPresent()) {
            if (itemBean.isAdd()) {
                total_item += diffSecureOpt.get().getQuantity();
            } else {
                total_item -= diffSecureOpt.get().getQuantity();
            }
        }
        boolean is_secure_item = isIsSecureItem(product, total_item, itemBean);

        if (!itemBean.isAdd() && sameItemOpt.isEmpty() && diffSecureOpt.isEmpty()) {
            return cartItems;
        }

        // Add or update the item
        Item item = new Item();
        item.setProduct_id(product.getId());
        item.setQuantity(newQuantity);
        item.setProduct_code(product.getProduct_code());
        item.set_secure(is_secure_item);
        updatedItems.add(item);

        // Add all other items (excluding the updated/removed one)
        updatedItems.addAll(cartItems.stream()
                .filter(cartItem -> !cartItem.getProduct_id().equals(productId) || cartItem.is_secure() != is_secure_item)
                .toList());
        return updatedItems;
    }

    @PostMapping("/cart/add")
    public SEResponse update(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            CartCRUDBean req = request.getGenericRequestDataObject(CartCRUDBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.CART_MANAGEMENT);
            if (req.getItem() == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_ITEMS);
            }

            CartItemsBean itemBean = req.getItem();
            if (!StringUtils.hasText(itemBean.getProduct_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_ID);
            }
            if (itemBean.getQuantity() == null || itemBean.getQuantity() < 0) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_QUANTITY);
            }

            SEFilter filterC = new SEFilter(SEFilterType.AND);
            filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
            filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Cart cart = cart_Service.repoFindOne(filterC);
            if (cart == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }
            if (CollectionUtils.isEmpty(cart.getCart_items())) {
                cart.setCart_items(new ArrayList<>());
            }

            List<Item> listItems = new ArrayList<>();
            if (itemBean.getQuantity() == 0) {
                for (Item cartItem : cart.getCart_items()) {
                    if (!cartItem.getProduct_id().equals(itemBean.getProduct_id()) || (cartItem.getProduct_id().equals(itemBean.getProduct_id()) && cartItem.is_secure() != itemBean.isSecure_item())) {
                        listItems.add(cartItem);
                    }
                }
            } else {

                SEFilter filterP = new SEFilter(SEFilterType.AND);
                filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, itemBean.getProduct_id()));
                filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                Products product = productService.repoFindOne(filterP);
                if (product == null) {
                    throw new CustomIllegalArgumentsException(ResponseCode.ITEM_NOT_FOUND);
                }
                long total_item = itemBean.getQuantity();

                Predicate<Item> p1 = x -> x.getProduct_id().equals(itemBean.getProduct_id());
                Predicate<Item> p2 = x -> x.is_secure() != itemBean.isSecure_item();
                Optional<Item> optional = cart.getCart_items().stream().filter(p1.and(p2)).findFirst();
                if (optional.isPresent()) {
                    total_item += optional.get().getQuantity();
                }
                boolean is_secure_item = isIsSecureItem(product, total_item, itemBean);

                Item item = new Item();
                item.setProduct_id(product.getId());
                item.setQuantity(itemBean.getQuantity());
                item.setProduct_code(product.getProduct_code());
                item.set_secure(is_secure_item);

                listItems.add(item);
                if (CollectionUtils.isEmpty(cart.getCart_items())) {
                    cart.setCart_items(new ArrayList<>());
                } else {
                    Predicate<Item> p3 = x -> !x.getProduct_id().equals(item.getProduct_id());
                    Predicate<Item> p4 = x -> x.is_secure() != is_secure_item;
                    List<Item> list = cart.getCart_items().stream().filter(p3.or(p4)).toList();
                    if (!CollectionUtils.isEmpty(list)) {
                        listItems.addAll(list);
                    }
                }
            }

            cart.setCart_items(listItems);
            cart_Service.update(cart.getId(), cart, usersBean.getId());

            CartBean cartBean = cartUtility.getCartBean(cart);
            return SEResponse.getBasicSuccessResponseObject(cartBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/add:: exception occurred");
            log.error("/add:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private static boolean isIsSecureItem(Products product, long total_item, CartItemsBean itemBean) {
        if (product.getQuantity().compareTo(total_item) < 0) {
            String message = product.getQuantity().compareTo(0L) > 0
                    ? "We only have " + product.getQuantity() + " in stock"
                    : ResponseCode.OUT_OF_STOCK.getUserMessage();
            throw new CustomIllegalArgumentsException(message);
        }

        boolean secure_item = product.getIs_secure();
        boolean is_secure_item = itemBean.isSecure_item();
        if (is_secure_item && !secure_item) {
            throw new CustomIllegalArgumentsException(ResponseCode.CANNOT_SECURE);
        }
        return is_secure_item;
    }

    @PostMapping("/cart/applyCoupon")
    public CartBeanV2 applyCoupon(@RequestBody ApplyCouponBean request, HttpServletRequest httpServletRequest) throws JsonProcessingException {
        CommonUtils.extractHeaders(httpServletRequest, request);
        UsersBean usersBean = users_Service.validateUserForActivity(request.getReq_user_id(), Activity.CART_MANAGEMENT);
        UserType userType = usersBean.getRole().getUser_type();
        switch (userType) {
            case CUSTOMER, GUEST:
                break;
            default:
                throw new AccessDeniedException();
        }
        Preconditions.check(StringUtils.hasText(request.getCouponCode()), ResponseCode.MISSING_COUPON_CODE);

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(filterC);
        Preconditions.check(cart != null, ResponseCode.NO_RECORD);

        if (StringUtils.hasText(cart.getCouponCode())) {
            cart.setCouponCode(null);
            cart_Service.update(cart.getId(), cart, usersBean.getId());
        }

        CartBeanV2 cartBeanV2 = cartUtility.getCartBeanV2(cart);

        couponUtility.validateCouponAndThrowException(request.getCouponCode(), CommonUtils.rupeeToPaise(cartBeanV2.getBillingSummary().getToPay()), usersBean.getId(), cartBeanV2.isFreeDelivery());

        cart.setCouponCode(request.getCouponCode());
        cart_Service.update(cart.getId(), cart, usersBean.getId());

        return cartUtility.getCartBeanV2(cart);
    }

    @PostMapping("/coupon/remove")
    public void removeCoupon(HttpServletRequest httpServletRequest) throws JsonProcessingException {
        String req_user_id = httpServletRequest.getHeader("req_user_id");
        UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, Activity.CART_MANAGEMENT);
        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(filterC);
        Preconditions.check(cart != null, ResponseCode.NO_RECORD);

        cart.setCouponCode(null);
        cart_Service.update(cart.getId(), cart, usersBean.getId());
    }

    @GetMapping("/cart/getAllCoupons")
    public CouponListResponse getAllCoupons(HttpServletRequest httpServletRequest) throws JsonProcessingException {
        String reqUserId = httpServletRequest.getHeader("req_user_id");
        Preconditions.check(StringUtils.hasText(reqUserId), new AccessDeniedException());
        UsersBean usersBean = users_Service.validateUserForActivity(reqUserId, Activity.CART_MANAGEMENT);

        LocalDateTime now = LocalDateTime.now();


        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.lte(CouponEntity.Fields.startDate, now));
        filter.addClause(WhereClause.gte(CouponEntity.Fields.endDate, now));

        List<CouponEntity> coupons = couponService.repoFind(filter);
        if (CollectionUtils.isEmpty(coupons)) {
            return CouponListResponse.builder()
                    .applicableCoupons(new ArrayList<>())
                    .otherCoupons(new ArrayList<>())
                    .build();
        }

        // Get user's cart
        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(filterC);
        if (cart == null) {
            return CouponListResponse.builder()
                    .applicableCoupons(new ArrayList<>())
                    .otherCoupons(new ArrayList<>())
                    .currentCartValue(BigDecimal.ZERO)
                    .build();
        }

        cart.setCouponCode(null);

        CartBeanV2 cartBean = cartUtility.getCartBeanV2(cart);
        if (CollectionUtils.isEmpty(cartBean.getCartItems())) {
            return CouponListResponse.builder()
                    .applicableCoupons(new ArrayList<>())
                    .otherCoupons(new ArrayList<>())
                    .currentCartValue(BigDecimal.ZERO)
                    .build();
        }

        Long cartValueInPaise = CommonUtils.rupeeToPaise(cartBean.getBillingSummary().getToPay());
        List<ApplicableCoupon> applicableCoupons = new ArrayList<>();
        List<OtherCoupon> otherCoupons = new ArrayList<>();

        return couponUtility.getApplicableCoupons(coupons, usersBean.getId(), cartValueInPaise, cartBean.getBillingSummary().getToPay().compareTo(CommonUtils.paiseToRupee(minCartValueInPaise)) > 0);

//        for (CouponEntity coupon : coupons) {
//
//            long discountAmount = couponUtility.calculateDiscountAmount(coupon, CommonUtils.rupeeToPaise(cartBean.getBillingSummary().getToPay()), cartBean.isFreeDelivery(), usersBean.getId());
////
////            // Check couponEntity scope
////            CouponScope couponScope = coupon.getCouponScope();
////            if (couponScope != null && couponScope.equals(CouponScope.USER_SPECIFIC)) {
////                if (CollectionUtils.isEmpty(coupon.getAssignedToUsers())) {
////                    continue;
////                }
////                boolean match = coupon.getAssignedToUsers().stream()
////                        .anyMatch(user -> user.equals(usersBean.getId()));
////                if (!match) {
////                    continue;
////                }
////            }
////
////            // Check if user has already used this couponEntity (if once per user)
////            if (coupon.isOncePerUser() && !CollectionUtils.isEmpty(coupon.getCouponUsages())) {
////                boolean alreadyUsed = coupon.getCouponUsages().stream()
////                        .anyMatch(usage -> usage.getUserId().equals(usersBean.getId()));
////                if (alreadyUsed) {
////                    continue;
////                }
////            }
////
////            // Check max uses
////            if (coupon.getMaxUses() != null && coupon.getUsedCount() != null
////                    && coupon.getUsedCount() >= coupon.getMaxUses()) {
////                continue;
////            }
////
////            // Check minimum cart value
////            Long minCartValue = coupon.getMinCartValue() != null ? coupon.getMinCartValue() : 0L;
//
//            if (discountAmount > 0) {
//                // CouponEntity is applicable
//                ApplicableCoupon applicableCoupon = createApplicableCoupon(coupon, cartValueInPaise);
//                applicableCoupons.add(applicableCoupon);
//            } else {
//                // CouponEntity needs more cart value
//                OtherCoupon otherCoupon = createOtherCoupon(coupon, cartValueInPaise, minCartValue);
//                otherCoupons.add(otherCoupon);
//            }
//        }
//
//        // Sort applicable coupons by discount amount (highest first)
//        applicableCoupons.sort((a, b) -> b.getCalculatedDiscount().compareTo(a.getCalculatedDiscount()));
//
//        // Mark the best offer
//        if (!applicableCoupons.isEmpty()) {
//            applicableCoupons.get(0).setBestOffer(true);
//        }
//
//        // Set sort order
//        for (int i = 0; i < applicableCoupons.size(); i++) {
//            applicableCoupons.get(i).setSortOrder(i + 1);
//        }
//
//        // Sort other coupons by additional amount needed (lowest first)
//        otherCoupons.sort(Comparator.comparing(OtherCoupon::getAdditionalAmountNeeded));
//
//        // Set sort order for other coupons
//        for (int i = 0; i < otherCoupons.size(); i++) {
//            otherCoupons.get(i).setSortOrder(i + 1);
//        }
//
//        return CouponListResponse.builder()
//                .applicableCoupons(applicableCoupons)
//                .otherCoupons(otherCoupons)
//                .currentCartValue(cartBean.getItem_total())
//                .deliveryCharge(cartBean.getDelivery_charge())
//                .totalAmount(cartBean.getTotal_amount())
//                .build();
    }

    private ApplicableCoupon createApplicableCoupon(CouponEntity coupon, BigDecimal calculatedDiscount, BigDecimal finalCartValue) {

        String savingsText = generateSavingsText(coupon, calculatedDiscount);

        return ApplicableCoupon.builder()
                .code(coupon.getCode())
                .name(coupon.getName())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue() != null ?
                        CommonUtils.paiseToRupee(coupon.getDiscountValue()) : null)
                .discountPercentage(coupon.getDiscountPercentage())
                .calculatedDiscount(calculatedDiscount)
                .finalCartValue(finalCartValue)
                .maxDiscount(coupon.getMaxDiscount() != null ?
                        CommonUtils.paiseToRupee(coupon.getMaxDiscount()) : null)
                .minCartValue(coupon.getMinCartValue() != null ?
                        CommonUtils.paiseToRupee(coupon.getMinCartValue()) : BigDecimal.ZERO)
                .endDate(coupon.getEndDate())
                .savingsText(savingsText)
                .isBestOffer(false)
                .build();
    }

    private OtherCoupon createOtherCoupon(CouponEntity coupon, Long cartValueInPaise, Long minCartValue) {
        BigDecimal additionalAmountNeeded = CommonUtils.paiseToRupee(minCartValue - cartValueInPaise);
        BigDecimal potentialDiscount = calculateCouponDiscount(coupon, minCartValue);

        String eligibilityText = String.format("Add ₹%.2f more to unlock this offer",
                additionalAmountNeeded.doubleValue());
        String notApplicableReason = "Minimum cart value not met";

        return OtherCoupon.builder()
                .code(coupon.getCode())
                .name(coupon.getName())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue() != null ?
                        CommonUtils.paiseToRupee(coupon.getDiscountValue()) : null)
                .discountPercentage(coupon.getDiscountPercentage())
                .minCartValue(CommonUtils.paiseToRupee(minCartValue))
                .additionalAmountNeeded(additionalAmountNeeded)
                .potentialDiscount(potentialDiscount)
                .maxDiscount(coupon.getMaxDiscount() != null ?
                        CommonUtils.paiseToRupee(coupon.getMaxDiscount()) : null)
                .endDate(coupon.getEndDate())
                .eligibilityText(eligibilityText)
                .notApplicableReason(notApplicableReason)
                .build();
    }

    private BigDecimal calculateCouponDiscount(CouponEntity coupon, Long cartValueInPaise) {
        BigDecimal discount = BigDecimal.ZERO;

        if (coupon.getDiscountType() == null) {
            return discount;
        }

        switch (coupon.getDiscountType()) {
            case PERCENTAGE:
                if (coupon.getDiscountPercentage() != null) {
                    BigDecimal cartValue = CommonUtils.paiseToRupee(cartValueInPaise);
                    discount = cartValue.multiply(coupon.getDiscountPercentage()).divide(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);

                    // Apply max discount cap if present
                    if (coupon.getMaxDiscount() != null && coupon.getMaxDiscount() > 0) {
                        BigDecimal maxDiscountInRupees = CommonUtils.paiseToRupee(coupon.getMaxDiscount());
                        discount = discount.min(maxDiscountInRupees);
                    }
                }
                break;

            case FIXED:
                if (coupon.getDiscountValue() != null) {
                    discount = CommonUtils.paiseToRupee(coupon.getDiscountValue());

                    // Ensure discount doesn't exceed cart value
                    BigDecimal cartValue = CommonUtils.paiseToRupee(cartValueInPaise);
                    discount = discount.min(cartValue);
                }
                break;

            default:
                // Handle any other discount types if they exist
                break;
        }

        return discount.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private String generateSavingsText(CouponEntity coupon, BigDecimal calculatedDiscount) {
        if (coupon.getDiscountType() == null) {
            return "";
        }

        return switch (coupon.getDiscountType()) {
            case PERCENTAGE -> {
                if (coupon.getMaxDiscount() != null && coupon.getMaxDiscount() > 0) {
                    BigDecimal maxDiscountInRupees = CommonUtils.paiseToRupee(coupon.getMaxDiscount());
                    if (calculatedDiscount.compareTo(maxDiscountInRupees) >= 0) {
                        yield String.format("You've saved ₹%.2f (Max discount reached)",
                                calculatedDiscount.doubleValue());
                    }
                }
                yield String.format("You've saved %s%% - ₹%.2f",
                        coupon.getDiscountPercentage().toPlainString(),
                        calculatedDiscount.doubleValue());
            }
            case FIXED -> String.format("Flat ₹%.2f off", calculatedDiscount.doubleValue());
            default -> String.format("You've saved ₹%.2f", calculatedDiscount.doubleValue());
        };
    }
}
