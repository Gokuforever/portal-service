package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.*;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.All_Status.ProductCurrentStatus;
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
import com.sorted.commons.porter.res.beans.GetQuoteResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.CouponUtility;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.assisting.beans.CartItemsBean;
import com.sorted.portal.request.beans.ApplyCouponBean;
import com.sorted.portal.request.beans.CartCRUDBean;
import com.sorted.portal.request.beans.CartFetchReqBean;
import com.sorted.portal.response.beans.FetchCartV2;
import com.sorted.portal.service.EstimateDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@RequestMapping("/cart")
@RestController
@RequiredArgsConstructor
public class ManageCart_BLService {

    private final Cart_Service cart_Service;
    private final ProductService productService;
    private final Users_Service users_Service;
    private final EstimateDeliveryService estimateDeliveryService;
    private final StoreActivityService storeActivityService;
    private final Seller_Service sellerService;
    private final DemandingPincodeService demandingPincodeService;
    private final Address_Service addressService;
    private final CouponService couponService;
    private final CouponUtility couponUtility;

    @Value("${se.minimum-cart-value.in-paise:10000}")
    private long minCartValueInPaise;

    @Value("${se.fixed-delivery-charge.in-paise:5900}")
    private long fixedDeliveryCharge;

    @GetMapping("/v2/fetch")
    public FetchCartV2 fetchV2(HttpServletRequest httpServletRequest) {
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
                    .totalCount(0)
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
                    .totalCount(0)
                    .totalAmount(BigDecimal.ZERO)
                    .freeDeliveryDiff(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .deliveryFree(false)
                    .savings(BigDecimal.ZERO)
                    .minimumCartValue(CommonUtils.paiseToRupee(minCartValueInPaise))
                    .build();
        }

        // Extract product IDs efficiently using parallel stream for large lists
        List<String> productIds = cartItems.parallelStream()
                .map(Item::getProduct_id)
                .distinct() // Remove duplicates to reduce DB load
                .collect(Collectors.toList());

        // Use projection to fetch only required fields: id, selling_price, mrp
        SEFilter productFilter = new SEFilter(SEFilterType.AND);
        productFilter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));
        productFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        productFilter.addProjection(BaseMongoEntity.Fields.id, Products.Fields.selling_price, Products.Fields.mrp);

        // Consider adding projection here if your service supports it:
        // productFilter.setProjection(Arrays.asList("id", "selling_price", "mrp"));

        List<Products> products = productService.repoFind(productFilter);

        // Use more efficient map creation with proper initial capacity
        Map<String, Products> productsMap = products.stream()
                .collect(Collectors.toMap(
                        Products::getId,
                        Function.identity(),
                        (existing, replacement) -> existing, // Handle potential duplicates
                        HashMap::new
                ));

        // Calculate totals using single pass with primitive operations
        long totalCartValueInPaise = 0L;
        long totalMrpInPaise = 0L;
        int totalCount = 0;

        for (Item item : cartItems) {
            Products product = productsMap.get(item.getProduct_id());
            if (product != null) {
                long quantity = item.getQuantity();
                totalCartValueInPaise += product.getSelling_price() * quantity;
                totalMrpInPaise += product.getMrp() * quantity;
                totalCount++;
            }
        }

        // Calculate derived values
        BigDecimal totalCartValue = CommonUtils.paiseToRupee(totalCartValueInPaise);
        BigDecimal freeDeliveryDiff = totalCartValueInPaise < minCartValueInPaise
                ? CommonUtils.paiseToRupee(minCartValueInPaise - totalCartValueInPaise)
                : BigDecimal.ZERO;

        long moneySavedInPaise = totalMrpInPaise - totalCartValueInPaise;
        boolean isDeliveryFree = totalCartValueInPaise >= minCartValueInPaise; // Use >= for boundary case

        if (isDeliveryFree) {
            moneySavedInPaise += fixedDeliveryCharge;
        }

        return FetchCartV2.builder()
                .totalCount(totalCount) // Use actual count of valid items
                .totalAmount(totalCartValue)
                .freeDeliveryDiff(freeDeliveryDiff)
                .deliveryFree(isDeliveryFree)
                .savings(CommonUtils.paiseToRupee(moneySavedInPaise))
                .minimumCartValue(CommonUtils.paiseToRupee(minCartValueInPaise))
                .build();
    }

    @PostMapping("/clear")
    public SEResponse clear(HttpServletRequest httpServletRequest) {
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

            CartBean cartBean = this.getCartBean(cart);
            return SEResponse.getBasicSuccessResponseObject(cartBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/clear:: exception occurred");
            log.error("/clear:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/fetch")
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
            CartBean cartBean = this.getCartBean(cart, address_id, usersBean.getFirst_name() + " " + usersBean.getLast_name());
            return SEResponse.getBasicSuccessResponseObject(cartBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/fetch:: exception occurred");
            log.error("/fetch:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/add")
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

            CartBean cartBean = this.getCartBean(cart);
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

    private CartBean getCartBean(Cart cart) throws JsonProcessingException {
        return this.getCartBean(cart, null, null);
    }

    private CartBean getCartBean(Cart cart, String address_id, String customerName) throws JsonProcessingException {
        CartBean cartBean = new CartBean();
        List<CartItems> cartItems = new ArrayList<>();
        List<Long> total_price_in_paise = new ArrayList<>();
        List<Long> total_cart_items = new ArrayList<>();
        List<Item> cart_items = cart.getCart_items();
        String seller_id = null;
        if (!CollectionUtils.isEmpty(cart_items)) {
            List<String> product_ids = cart_items.stream().map(Item::getProduct_id).toList();
            SEFilter filterP = new SEFilter(SEFilterType.AND);
            filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, product_ids));
//			filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Products> listP = productService.repoFind(filterP);
            if (!CollectionUtils.isEmpty(listP)) {
                seller_id = listP.get(0).getSeller_id();
                Map<String, Products> mapP = listP.stream().collect(Collectors.toMap(BaseMongoEntity::getId, p -> p));

                cart_items.forEach(e -> {
                    if (mapP.containsKey(e.getProduct_id())) {
                        CartItems items = new CartItems();
                        Products products = mapP.get(e.getProduct_id());
                        items.setProduct_name(products.getName());
                        items.setProduct_code(e.getProduct_code());
                        items.setProduct_id(e.getProduct_id());
                        items.setQuantity(e.getQuantity());
                        items.setSelling_price(CommonUtils.paiseToRupee(products.getSelling_price()));
                        items.setSecure_item(e.is_secure());
                        if (products.isDeleted()) {
                            items.setCurrent_status(ProductCurrentStatus.CURRENTLY_UNAVAILABLE.getStatus_id());
                        } else if (products.getQuantity().compareTo(e.getQuantity()) >= 0) {
                            total_price_in_paise.add(products.getSelling_price() * items.getQuantity());
                            items.setCurrent_status(ProductCurrentStatus.IN_STOCK.getStatus_id());
                            total_cart_items.add(items.getQuantity());
                        } else {
                            items.setCurrent_status(ProductCurrentStatus.OUT_OF_STOCK.getStatus_id());
                        }
                        List<Media> media = products.getMedia();
                        if (!CollectionUtils.isEmpty(media)) {
                            Optional<Media> findFirst = media.stream().filter(m -> m.getOrder() == 0).findFirst();
                            findFirst.ifPresent(value -> items.setCdn_url(value.getCdn_url()));
                        }
                        cartItems.add(items);
                    }
                });
            }
        }
        long summed = total_price_in_paise.stream().mapToLong(Long::longValue).sum();
        long total_items = total_cart_items.stream().mapToLong(Long::longValue).sum();
        boolean addressPresent = StringUtils.hasText(address_id);
        if (addressPresent && summed > 0) {
            Seller seller = sellerService.findById(seller_id).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND));
            GetQuoteResponse quote = estimateDeliveryService.getEstimateDeliveryAmount(address_id, seller.getAddress_id(), customerName);
            if (quote != null) {
                cart.setDelivery_charges(fixedDeliveryCharge);
                cart_Service.update(cart.getId(), cart, cart.getModified_by());
            } else {
                addressService.findById(address_id).ifPresent(address ->
                        demandingPincodeService.storeDemandingPincode(address.getPincode(), cart.getUser_id()));
            }
        }
        boolean freeDelivery = minCartValueInPaise <= summed;
        cartBean.setItem_total(CommonUtils.paiseToRupee(summed));
        cartBean.setTotal_count(total_items);
        cartBean.setCart_items(cartItems);
        cartBean.setDelivery_charge(total_items > 0 ? CommonUtils.paiseToRupee(fixedDeliveryCharge) : BigDecimal.ZERO);
        cartBean.setTotal_amount(summed > 0 ? freeDelivery ? CommonUtils.paiseToRupee(summed) : CommonUtils.paiseToRupee(summed + fixedDeliveryCharge) : BigDecimal.ZERO);
        cartBean.set_free_delivery(freeDelivery);
        cartBean.setStoreOperational(storeActivityService.isStoreOperational(seller_id));

        return cartBean;
    }

    @PostMapping("/applyCoupon")
    public void applyCoupon(@RequestBody ApplyCouponBean request, HttpServletRequest httpServletRequest) throws JsonProcessingException {
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
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(CouponEntity.Fields.code, request.getCouponCode()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        CouponEntity coupon = couponService.repoFindOne(filter);
        Preconditions.check(coupon != null, ResponseCode.COUPON_CODE_NOT_FOUND);

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(filterC);
        Preconditions.check(cart != null, ResponseCode.NO_RECORD);

        CartBean cartBean = this.getCartBean(cart);

        Long discountAmount = couponUtility.validateCouponAndGetDiscount(cartBean, coupon, usersBean.getId());
        cart.setCouponCode(coupon.getCode());
        cartBean.setCouponCode(coupon.getCode());
        cartBean.setDiscountAmount(CommonUtils.paiseToRupee(discountAmount));

    }

}
