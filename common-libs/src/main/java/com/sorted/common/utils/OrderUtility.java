package com.sorted.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.common.beans.*;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.*;
import com.sorted.common.enums.*;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.service.ZoneHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class OrderUtility {
    private final Cart_Service cart_Service;
    private final ProductService productService;
    private final Address_Service address_Service;
    private final Order_Details_Service order_Details_Service;
    private final Seller_Service seller_Service;
    private final OrderService orderService;
    private final CartUtility cartUtility;
    private final ComboUtility comboUtility;
    private final ProductUtility productUtility;
    private final ZoneHandlerService zoneHandlerService;
    private final Users_Service usersService;

    @Value("${se.fixed-delivery-charge.in-paise:4000}")
    private long fixedDeliveryFee;

    @Value("${se.minimum-cart-value.in-paise:39900}")
    private long minCartValueInPaise;

    @Value("${se.small-cart-fee.in-paise:1000}")
    private long fixedSmallCartFee;

    @Value("${se.handling-fee.in-paise:900}")
    private long fixedHandlingFee;

    public Order_Details validateAndCreateOrder(PayNowBean req, UsersBean usersBean) throws JsonProcessingException {
        // Validate delivery address
        log.debug("pay:: Starting delivery address validation");
        Address address = validateDeliveryAddress(req, usersBean);
        log.info("pay:: Delivery address validation successful. Address ID: {}, Pincode: {}",
                address.getId(), address.getPincode());

        ZoneEntity zoneEntity = zoneHandlerService.identifyZone(address.getLat().doubleValue(), address.getLng().doubleValue());
        if (!zoneEntity.getZoneId().equals(usersBean.getNearestZoneId())) {
            Users users = usersService.findById(usersBean.getId()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND));
            users.setCurrentLat(address.getLat());
            users.setCurrentLng(address.getLng());
            users.setNearestZoneId(zoneEntity.getZoneId());
            usersService.update(users.getId(), users, Defaults.SYSTEM_ADMIN);
            throw new CustomIllegalArgumentsException("Please refresh the page and try again");
        }

        // Validate cart
        log.debug("pay:: Starting cart validation");
        Cart cart = validateCart(usersBean);
        log.info("pay:: Cart validation successful. Cart ID: {}, Items count: {}",
                cart.getId(), cart.getCart_items() != null ? cart.getCart_items().size() : 0);

        CartBeanV2 cartBean = cartUtility.getCartBeanV2(cart);

        Preconditions.check(!cartBean.getCartItems().isEmpty(), ResponseCode.CART_EMPTY);
        Preconditions.check(cartBean.getBillingSummary().getToPay().compareTo(BigDecimal.ZERO) > 0, ResponseCode.CART_EMPTY);
        // Validate seller
        log.debug("pay:: Starting seller validation");
        Seller seller = validateSeller(cartBean.getCartItems());
        log.info("pay:: Seller validation successful. Seller ID: {}", seller.getId());

        // Get seller address
        log.debug("pay:: Fetching seller address");
        Address sellerAddress = getSellerAddress(seller);
        log.info("pay:: Seller address retrieved. Address ID: {}, Pincode: {}",
                sellerAddress.getId(), sellerAddress.getPincode());


        long totalSum = CommonUtils.rupeeToPaise(cartBean.getBillingSummary().getToPay());
        long totalItemsCost = CommonUtils.rupeeToPaise(cartBean.getBillingSummary().getTotalSellingPrice());
        long deliveryCharges = CommonUtils.rupeeToPaise(cartBean.getBillingSummary().getActualDeliveryFee());
        long handlingCharges = CommonUtils.rupeeToPaise(cartBean.getBillingSummary().getActualHandlingFee());
        long smallCartFee = CommonUtils.rupeeToPaise(cartBean.getBillingSummary().getActualSmallCartFee());
        long totalDiscount = CommonUtils.rupeeToPaise(cartBean.getBillingSummary().getCouponDiscount());

        Order_Details order = createOrder(usersBean, seller.getId(), totalSum, totalItemsCost, handlingCharges, smallCartFee, totalDiscount, deliveryCharges, address, sellerAddress, cartBean.getBillingSummary().getCouponCode());

        // Reduce product quantity
        log.debug("pay:: Reducing product quantities");
        orderService.reduceProductQuantity(cartBean.getCartItems());
        log.info("pay:: Product quantities reduced successfully");

        // Reduce cart quantity
        log.debug("pay:: Emptying user cart");
        orderService.emptyCart(cart.getId(), usersBean.getId());
        log.info("pay:: Cart emptied successfully");

        List<Order_Item> orderItems = createOrderItems(cartBean.getCartItems(), usersBean.getId(), totalDiscount);

        // Save order
        log.debug("pay:: Persisting order to database");
        order = order_Details_Service.create(order, usersBean.getId());
        log.info("pay:: Order saved successfully with ID: {}, Code: {}",
                order.getId(), order.getCode());

        // Create order items in DB
        log.debug("pay:: Creating order items in database");
        orderService.createOrderItems(orderItems, order.getId(), order.getCode(), usersBean.getId());
        log.info("pay:: Order items created successfully for order: {}", order.getId());
        return order;
    }

    private Address validateDeliveryAddress(PayNowBean req, UsersBean usersBean) throws JsonProcessingException {
        log.debug("validateDeliveryAddress:: Validating address: {}", req.getDelivery_address_id());

        if (!StringUtils.hasText(req.getDelivery_address_id())) {
            log.error("validateDeliveryAddress:: Delivery address ID is missing");
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_DELIVERY_ADD);
        }

        SEFilter filterA = new SEFilter(SEFilterType.AND);
        filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getDelivery_address_id()));
        filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));
        filterA.addClause(WhereClause.eq(Address.Fields.user_type, UserType.CUSTOMER.name()));

        Address address = address_Service.repoFindOne(filterA);
        if (address == null) {
            log.error("validateDeliveryAddress:: Address not found: {}", req.getDelivery_address_id());
            throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
        }

        return address;
    }

    private Cart validateCart(UsersBean usersBean) {
        log.debug("validateCart:: Validating cart for user: {}", usersBean.getId());

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(filterC);
        if (cart == null) {
            log.error("validateCart:: No cart found for user: {}", usersBean.getId());
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        List<Item> cart_items = cart.getCart_items();
        if (CollectionUtils.isEmpty(cart_items)) {
            log.error("validateCart:: Cart is empty for user: {}", usersBean.getId());
            throw new CustomIllegalArgumentsException(ResponseCode.CART_EMPTY);
        }

        log.debug("validateCart:: Cart validation successful, {} items found", cart_items.size());
        return cart;
    }

    private Seller validateSeller(List<CartItems> cartItems) {
        Optional<CartItems> firstItem = cartItems.stream().findFirst();
        Preconditions.check(firstItem.isPresent(), ResponseCode.CART_EMPTY);
        String productId = firstItem.get().getProduct_id();
        Optional<Products> productsOptional = productService.findById(productId);
        if (productsOptional.isEmpty()) {
            log.error("validateSeller:: Product not found: {}", productId);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }

        String seller_id = productsOptional.get().getSeller_id();
        log.debug("validateSeller:: Using seller ID: {}", seller_id);

        SEFilter filterS = new SEFilter(SEFilterType.AND);
        filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, seller_id));
        filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Seller seller = seller_Service.repoFindOne(filterS);
        if (seller == null) {
            log.error("validateSeller:: Seller not found: {}", seller_id);
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        log.debug("validateSeller:: Seller validation successful");
        return seller;
    }

    private Address getSellerAddress(Seller seller) {
        SEFilter filterSA = new SEFilter(SEFilterType.AND);
        filterSA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterSA.addClause(WhereClause.eq(Address.Fields.entity_id, seller.getId()));
        filterSA.addClause(WhereClause.eq(Address.Fields.user_type, UserType.SELLER.name()));

        Address sellerAddress = address_Service.repoFindOne(filterSA);
        if (sellerAddress == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
        }
        return sellerAddress;
    }

    private List<Order_Item> createOrderItems(List<CartItems> cartItems, String userId, long totalDiscount) {
        log.debug("createOrderItems:: Creating order items for {} cart items", cartItems.size());
        List<Order_Item> listOI = new ArrayList<>();

//        List<String> comboIds = cartItems.stream().filter(e -> e.getCurrent_status().equals(All_Status.ProductCurrentStatus.IN_STOCK.getStatus_id()) && e.is_combo()).map(CartItems::getProduct_id).toList();
//
//        if (!CollectionUtils.isEmpty(comboIds)) {
//            List<Combo> activeCombos = comboUtility.getActiveCombos(comboIds);
//
//            if (!CollectionUtils.isEmpty(activeCombos)) {
//                List<String> comboProductIds = activeCombos.stream().flatMap(combo -> combo.getItem_ids().stream()).distinct().toList();
//
//                SEFilter filterP = new SEFilter(SEFilterType.AND);
//                filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, comboProductIds));
//                filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//
//                List<Products> listP = productService.repoFind(filterP);
//                if (CollectionUtils.isEmpty(listP)) {
//                    return listOI;
//                }
//
//                Map<String, Products> productsMap = listP.stream().collect(Collectors.toMap(Products::getId, p -> p));
//
//                Map<String, Combo> comboMap = activeCombos.stream().collect(Collectors.toMap(BaseMongoEntity::getId, combo -> combo));
//
//                for (CartItems item : cartItems) {
//                    Combo combo = comboMap.getOrDefault(item.getProduct_id(), null);
//                    if (combo == null) {
//                        continue;
//                    }
//                    List<String> itemIds = combo.getItem_ids();
//                    for (String itemId : itemIds) {
//                        Products products = productsMap.getOrDefault(itemId, null);
//                        if (products == null) {
//                            continue;
//                        }
//                        Order_Item orderItem = getOrderItem(item, combo, products, userId);
//                        listOI.add(orderItem);
//                    }
//                }
//            }
//        }

        List<String> productIds = cartItems.stream().filter(e -> e.getCurrent_status().equals(All_Status.ProductCurrentStatus.IN_STOCK.getStatus_id()) && !e.is_combo()).map(CartItems::getProduct_id).toList();

        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Products> listP = productService.repoFind(filterP);
        if (CollectionUtils.isEmpty(listP)) {
            return listOI;
        }

        Map<String, Products> productsMap = listP.stream().collect(Collectors.toMap(Products::getId, p -> p));

        Map<String, Long> productHighestSellingPrice = productUtility.getProductHighestSellingPrice(listP.stream().map(Products::getProduct_master_id).toArray(String[]::new));

        for (CartItems cartItem : cartItems) {
            Products products = productsMap.getOrDefault(cartItem.getProduct_id(), null);
            if (products == null) {
                continue;
            }
            if (!cartItem.getCurrent_status().equals(All_Status.ProductCurrentStatus.IN_STOCK.getStatus_id())) {
                continue;
            }

            if (products.getQuantity().compareTo(cartItem.getQuantity()) < 0) {
                log.error("createOrderItems:: Insufficient stock for product: {}. Available: {}, Requested: {}",
                        products.getId(), products.getQuantity(), cartItem.getQuantity());
                throw new CustomIllegalArgumentsException(ResponseCode.FEW_OUT_OF_STOCK);
            }

            Order_Item order_Item = getOrderItem(cartItem, products, userId, productHighestSellingPrice);
            listOI.add(order_Item);
            log.debug("createOrderItems:: Created order item for product: {}, quantity: {}, total: {}",
                    products.getId(), products.getQuantity(), order_Item.getTotal_cost());
        }

        long totalOrderValue = 0L;

        for (Order_Item item : listOI) {
            totalOrderValue += item.getSelling_price(); // in paise
        }

        long remainingDiscount = totalDiscount;

        for (int i = 0; i < listOI.size(); i++) {

            Order_Item item = listOI.get(i);
            long itemDiscount;

            if (i == listOI.size() - 1) {
                itemDiscount = remainingDiscount;
            } else {
                itemDiscount = (item.getSelling_price() * totalDiscount) / totalOrderValue;
                itemDiscount = Math.min(itemDiscount, item.getSelling_price());
                remainingDiscount -= itemDiscount;
            }

            long finalPrice = item.getSelling_price() - itemDiscount;
            item.setSelling_price_after_discount(Math.max(finalPrice, 0));
        }

        log.info("createOrderItems:: Successfully created {} order items", listOI.size());
        return listOI;
    }

//    private Order_Item getOrderItem(CartItems item, Combo combo, Products product, String userId) {
//
//        Order_Item order_Item = new Order_Item();
//        order_Item.setCombo(true);
//        order_Item.setCombo_id(combo.getId());
//        order_Item.setCombo_code(combo.getCode());
//        order_Item.setCombo_name(combo.getName());
//        order_Item.setCombo_description(combo.getDescription());
//        order_Item.setCombo_img_src(!CollectionUtils.isEmpty(combo.getMedia()) ? combo.getMedia().get(0).getCdn_url() : null);
//        order_Item.setCombo_selling_price(combo.getSelling_price());
//        order_Item.setCombo_mrp(combo.getMrp());
//        order_Item.setProduct_id(product.getId());
//        order_Item.setProduct_code(product.getProduct_code());
//        order_Item.setProduct_name(product.getName());
//        order_Item.setCdn_url(!CollectionUtils.isEmpty(product.getMedia()) ? product.getMedia().get(0).getCdn_url() : null);
//        order_Item.setQuantity(item.getQuantity());
//        order_Item.setSelling_price(product.getSelling_price());
//        order_Item.setSeller_id(product.getSeller_id());
//        order_Item.setSeller_code(product.getSeller_code());
//        order_Item.setStatus(OrderStatus.ORDER_PLACED, userId);
//        order_Item.setTotal_cost(product.getSelling_price() * item.getQuantity());
//
//        order_Item.setType(PurchaseType.BUY);
//
//        return order_Item;
//    }

    private Order_Item getOrderItem(CartItems item, Products product, String userId, Map<String, Long> productHighestSellingPrice) {
        Long sellingPrice = productHighestSellingPrice.get(product.getProduct_master_id());
        Order_Item order_Item = new Order_Item();
        order_Item.setProduct_id(product.getId());
        order_Item.setProduct_code(product.getProduct_code());
        order_Item.setProduct_name(product.getName());
        order_Item.setCdn_url(!CollectionUtils.isEmpty(product.getMedia()) ? product.getMedia().get(0).getCdn_url() : null);
        order_Item.setQuantity(item.getQuantity());
        order_Item.setSelling_price(sellingPrice);
        order_Item.setSeller_id(product.getSeller_id());
        order_Item.setSeller_code(product.getSeller_code());
        order_Item.setStatus(OrderStatus.ORDER_PLACED, userId);
        order_Item.setTotal_cost(sellingPrice * item.getQuantity());
        if (item.isSecure_item()) {
            order_Item.setType(PurchaseType.SECURE);
        } else {
            order_Item.setType(PurchaseType.BUY);
        }
        return order_Item;
    }

    private Order_Details createOrder(UsersBean usersBean,
                                      String seller_id,
                                      long totalSum,
                                      long totalItemsCost,
                                      long handlingCharges,
                                      long smallCartFee,
                                      long totalDiscount,
                                      long deliveryCharges,
                                      Address deliveryAddress,
                                      Address sellerAddress,
                                      String couponCode) {

        Order_Details order = new Order_Details();

        order.setUser_id(usersBean.getId());
        order.setSeller_id(seller_id);
        order.setTotal_amount(totalSum);
        order.setTotal_items_cost(totalItemsCost);
        order.setDelivery_charges(deliveryCharges);
        order.setHandling_charges(handlingCharges);
        order.setSmall_cart_fee(smallCartFee);
        order.setTotal_discount(totalDiscount);

        AddressDTO pickupAddress = createAddressDTOFromAddress(sellerAddress);
        AddressDTO delAddress = createAddressDTOFromAddress(deliveryAddress);

        order.setPickup_address(pickupAddress);
        order.setDelivery_address(delAddress);
        order.setEstimated_delivery_charges(deliveryCharges);
        order.setCoupon_code(couponCode);
        order.setStatus(OrderStatus.ORDER_PLACED, usersBean.getId());
        return order;
    }

    private AddressDTO createAddressDTOFromAddress(Address address) {
        AddressDTO addressDTO = new AddressDTO();
        if (StringUtils.hasText(address.getStreet_1())) {
            addressDTO.setStreet_1(address.getStreet_1());
        }
        if (StringUtils.hasText(address.getStreet_2())) {
            addressDTO.setStreet_2(address.getStreet_2());
        }
        if (StringUtils.hasText(address.getLandmark())) {
            addressDTO.setLandmark(address.getLandmark());
        }
        if (StringUtils.hasText(address.getCity())) {
            addressDTO.setCity(address.getCity());
        }
        if (StringUtils.hasText(address.getState())) {
            addressDTO.setState(address.getState());
        }
        if (StringUtils.hasText(address.getPincode())) {
            addressDTO.setPincode(address.getPincode());
        }
        if (address.getAddress_type() != null) {
            addressDTO.setAddress_type(address.getAddress_type().name());
        }
        if (StringUtils.hasText(address.getAddress_type_desc())) {
            addressDTO.setAddress_type_desc(address.getAddress_type_desc());
        }
        if (StringUtils.hasText(address.getPhone_no())) {
            addressDTO.setPhone_no(address.getPhone_no());
        }
        addressDTO.setLat(address.getLat());
        addressDTO.setLng(address.getLng());
        addressDTO.setFirst_name(address.getFirstName());
        addressDTO.setLast_name(address.getLastName());


        return addressDTO;
    }

    public static void main(String[] args) {
        System.out.println(BigDecimal.ZERO.compareTo(BigDecimal.TEN));
    }

}
