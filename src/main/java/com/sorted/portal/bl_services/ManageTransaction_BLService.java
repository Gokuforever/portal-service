package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.phonepe.sdk.pg.payments.v2.models.response.StandardCheckoutPayResponse;
import com.sorted.commons.beans.*;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.porter.res.beans.GetQuoteResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.GsonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.request.beans.PayNowBean;
import com.sorted.portal.response.beans.AddressResponse;
import com.sorted.portal.response.beans.FindOneOrder;
import com.sorted.portal.response.beans.OrderItemResponse;
import com.sorted.portal.response.beans.PayNowResponse;
import com.sorted.portal.service.OrderService;
import com.sorted.portal.service.order.OrderStatusCheckService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.NotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageTransaction_BLService {

    private final Users_Service users_Service;
    private final Cart_Service cart_Service;
    private final ProductService productService;
    private final Address_Service address_Service;
    private final Order_Details_Service order_Details_Service;
    private final Seller_Service seller_Service;
    private final Order_Dump_Service orderDumpService;
    private final OrderService orderService;
    private final PhonePeUtility phonePeUtility;
    private final OrderStatusCheckService orderStatusCheckService;
    private final PorterUtility porterUtility;
    private final DemandingPincodeService demandingPincodeService;

    @Value("${se.minimum-cart-value.in-paise:10000}")
    private long minCartValueInPaise;

    @GetMapping("/status")
    public FindOneOrder status(@RequestParam("orderId") String orderId, HttpServletRequest httpServletRequest) {
        if (!StringUtils.hasText(orderId)) {
            log.error("status:: Order ID is empty or null");
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
        }

        String req_user_id = httpServletRequest.getHeader("req_user_id");

        UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, Activity.PURCHASE);
        if (!(Objects.requireNonNull(usersBean.getRole().getUser_type()) == UserType.CUSTOMER)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }

        log.info("status:: Checking status for order ID: {}", orderId);

        Order_Details order_Details = findOrderDetails(orderId, usersBean.getId());

        try {
            List<OrderItemResponse> orderItemResponseList = orderStatusCheckService.checkOrderStatus(order_Details);

            // Build the complete response with code field included
            AddressResponse addressResponse = buildAddressResponse(order_Details.getDelivery_address());
            return FindOneOrder.builder()
                    .id(orderId)
                    .code(order_Details.getCode())
                    .paymentStatus(order_Details.getPayment_status())
                    .paymentMode(order_Details.getPayment_mode())
                    .totalAmount(order_Details.getTotal_amount())
                    .status(order_Details.getStatus().getCustomer_status())
                    .transactionId(order_Details.getTransaction_id())
                    .orderItems(orderItemResponseList)
                    .deliveryAddress(addressResponse)
                    .build();
        } catch (NotFoundException e) {
            log.error("status:: Order not found: {}", e.getMessage());
            throw e;
        } catch (CustomIllegalArgumentsException e) {
            log.error("status:: Custom exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("status:: Unexpected error occurred for order ID {}: {}", orderId, e.getMessage(), e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private Order_Details findOrderDetails(String orderId, String userId) {
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.eq(Order_Details.Fields.user_id, userId));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, orderId));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Order_Details order_Details = order_Details_Service.repoFindOne(filterOD);
        if (order_Details == null) {
            log.error("status:: Order not found with ID: {}", orderId);
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }
        return order_Details;
    }

    private AddressResponse buildAddressResponse(AddressDTO deliveryAddress) {
        return AddressResponse.builder()
                .addressType(deliveryAddress.getAddress_type())
                .city(deliveryAddress.getCity())
                .pincode(deliveryAddress.getPincode())
                .state(deliveryAddress.getState())
                .street_1(deliveryAddress.getStreet_1())
                .street_2(deliveryAddress.getStreet_2())
                .landmark(deliveryAddress.getLandmark())
                .addressTypeDesc(deliveryAddress.getAddress_type_desc())
                .phone_no(deliveryAddress.getPhone_no())
                .build();
    }

    @PostMapping("/pay")
    public PayNowResponse pay(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        Order_Dump orderDump = new Order_Dump(GsonUtils.getGson().toJson(request.getRequestData()));
        orderDumpService.create(orderDump, this.getClass().getSimpleName());
        try {
            log.info("/pay:: API started!");
            PayNowBean req = request.getGenericRequestDataObject(PayNowBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);

            // Validate user
            UsersBean usersBean = validateUserForPayment(req);

            // Validate delivery address
            Address address = validateDeliveryAddress(req, usersBean);

            // Validate cart
            Cart cart = validateCart(usersBean);

            // Process cart items
            List<Item> cart_items = cart.getCart_items();

            // Get products
            List<Products> listP = getProductsForCart(cart_items);

            // Validate seller
            Seller seller = validateSeller(listP);

            // Get seller address
            Address sellerAddress = getSellerAddress(seller);

            // Create order items
            Map<String, Products> mapP = listP.stream().collect(Collectors.toMap(BaseMongoEntity::getId, e -> e));
            List<Order_Item> listOI = createOrderItems(cart_items, mapP, usersBean.getId());

            // Calculate total
            Map<String, Long> mapSecurePQ = listOI.stream().filter(e -> e.getType() == PurchaseType.SECURE)
                    .collect(Collectors.toMap(Order_Item::getProduct_id, Order_Item::getQuantity));
            Map<String, Long> mapDirectPQ = listOI.stream().filter(e -> e.getType() == PurchaseType.BUY)
                    .collect(Collectors.toMap(Order_Item::getProduct_id, Order_Item::getQuantity));
            long totalSum = listOI.stream().mapToLong(Order_Item::getTotal_cost).sum();
            if (totalSum < 1) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_AMOUNT);
            }
            if (minCartValueInPaise >= totalSum) {
                if (cart.getDelivery_charges() == null || cart.getDelivery_charges() <= 0) {

                    GetQuoteResponse quote = porterUtility.getEstimateDeliveryAmount(address.getId(), seller.getAddress_id(), usersBean.getMobile_no(), usersBean.getFirst_name() + " " + usersBean.getLast_name());
                    if (quote == null) {
                        demandingPincodeService.storeDemandingPincode(address.getPincode(), usersBean.getId());
                        throw new CustomIllegalArgumentsException(ResponseCode.NOT_DELIVERIBLE);
                    }
                    cart.setDelivery_charges(quote.getVehicle().getFare().getMinor_amount());
                    cart_Service.update(cart.getId(), cart, usersBean.getId());
                }
                totalSum += cart.getDelivery_charges();
            }
//            if (minCartValueInPaise <= totalSum && cart.getDelivery_charges() != null && cart.getDelivery_charges() > 0) {
//                totalSum += cart.getDelivery_charges();
//            }

            // Create order
            Order_Details order = createOrder(usersBean, seller.getId(), totalSum, address, sellerAddress, cart.getDelivery_charges() == null ? 0L : cart.getDelivery_charges());

            // Reduce product quantity
            orderService.reduceProductQuantity(mapP.values().stream().toList(), mapSecurePQ);
            orderService.reduceProductQuantity(mapP.values().stream().toList(), mapDirectPQ);

            // Reduce cart quantity
            orderService.emptyCart(cart.getId(), usersBean.getId());

            // Save order
            Order_Details order_Details = order_Details_Service.create(order, usersBean.getId());

            // Create order items in DB
            orderService.createOrderItems(listOI, order_Details.getId(), order_Details.getCode(), usersBean.getId());

            // Create payment order
            Optional<StandardCheckoutPayResponse> checkoutPayResponseOptional = phonePeUtility.createOrder(order_Details.getId(), totalSum);

            if (checkoutPayResponseOptional.isEmpty()) {
                log.error("/pay:: Failed to create PhonePe order for order ID: {}", order_Details.getId());
                throw new CustomIllegalArgumentsException(ResponseCode.PG_ORDER_GEN_FAILED);
            }

            // Update order with payment details
            StandardCheckoutPayResponse checkoutPayResponse = checkoutPayResponseOptional.get();
            String pgOrderId = checkoutPayResponse.getOrderId();
            order_Details.setPg_order_id(pgOrderId);
            order_Details_Service.update(order_Details.getId(), order_Details, usersBean.getId());


            // Build response
            String redirectUrl = checkoutPayResponse.getRedirectUrl();
            String orderId = order_Details.getId();

            PayNowResponse payNowResponse = PayNowResponse.builder().redirectUrl(redirectUrl).orderId(orderId).build();
            orderDumpService.markSuccess(orderDump, order_Details.getId(), this.getClass().getSimpleName());
            return payNowResponse;
        } catch (CustomIllegalArgumentsException ex) {
            orderDumpService.markFailed(orderDump, ex.getResponseCode().getErrorMessage(), ex.getResponseCode().getCode(), this.getClass().getSimpleName());
            throw ex;
        } catch (Exception e) {
            log.error("/pay:: exception occurred");
            log.error("/pay:: {}", e.getMessage());
            orderDumpService.markFailed(orderDump, e.getMessage(), ResponseCode.ERR_0001.getCode(), this.getClass().getSimpleName());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private UsersBean validateUserForPayment(PayNowBean req) {
        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PURCHASE);
        switch (usersBean.getRole().getUser_type()) {
            case CUSTOMER -> { /* valid user type */ }
            case GUEST -> throw new CustomIllegalArgumentsException(ResponseCode.PROMT_SIGNUP);
            default -> throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
        return usersBean;
    }

    private Address validateDeliveryAddress(PayNowBean req, UsersBean usersBean) throws JsonProcessingException {
        if (!StringUtils.hasText(req.getDelivery_address_id())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_DELIVERY_ADD);
        }

        SEFilter filterA = new SEFilter(SEFilterType.AND);
        filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getDelivery_address_id()));
        filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));
        filterA.addClause(WhereClause.eq(Address.Fields.user_type, UserType.CUSTOMER.name()));

        Address address = address_Service.repoFindOne(filterA);
        if (address == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
        }
        String nearestSeller = usersBean.getNearestSeller();
        if (nearestSeller == null) {
            NearestSellerRes nearestSellerRes = porterUtility.getNearestSeller(address.getPincode(), usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getId());
            nearestSeller = nearestSellerRes.getSeller_id();
            Users users = users_Service.findById(usersBean.getId()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.NO_RECORD));
            users.setNearestSeller(nearestSeller);
            users_Service.update(users.getId(), users, Defaults.SYSTEM_ADMIN);
            usersBean.setNearestSeller(nearestSeller);
        }
        return address;
    }

    private Cart validateCart(UsersBean usersBean) {
        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(filterC);
        if (cart == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        List<Item> cart_items = cart.getCart_items();
        if (CollectionUtils.isEmpty(cart_items)) {
            throw new CustomIllegalArgumentsException(ResponseCode.CART_EMPTY);
        }
        return cart;
    }

    private List<Products> getProductsForCart(List<Item> cart_items) {
        Set<String> product_ids = cart_items.stream().map(Item::getProduct_id).collect(Collectors.toSet());

        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(product_ids)));
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Products> listP = productService.repoFind(filterP);
        if (CollectionUtils.isEmpty(listP)) {
            throw new CustomIllegalArgumentsException(ResponseCode.OUT_OF_STOCK);
        }
        return listP;
    }

    private Seller validateSeller(List<Products> listP) {
        List<String> seller_ids = listP.stream().map(Products::getSeller_id).distinct().toList();
        if (seller_ids.isEmpty()) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SELLER_FOR_ORDER);
        }
        String seller_id = seller_ids.get(0);
        SEFilter filterS = new SEFilter(SEFilterType.AND);
        filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, seller_id));
        filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Seller seller = seller_Service.repoFindOne(filterS);
        if (seller == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }
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

    private List<Order_Item> createOrderItems(List<Item> cart_items, Map<String, Products> mapP, String userId) {
        List<Order_Item> listOI = new ArrayList<>();

        for (Item item : cart_items) {
            if (!mapP.containsKey(item.getProduct_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.DELETED_PRODUCT);
            }
            Products product = mapP.get(item.getProduct_id());
            if (product == null) {
                continue;
            }
            if (product.getQuantity().compareTo(item.getQuantity()) < 0) {
                throw new CustomIllegalArgumentsException(ResponseCode.FEW_OUT_OF_STOCK);
            }

            Order_Item order_Item = getOrderItem(item, product, userId);
            listOI.add(order_Item);
        }
        return listOI;
    }

    private Order_Details createOrder(UsersBean usersBean, String seller_id, long totalSum, Address deliveryAddress, Address sellerAddress, long deliveryCharges) {
        Order_Details order = new Order_Details();

        if (StringUtils.hasText(usersBean.getId())) {
            order.setUser_id(usersBean.getId());
        }
        order.setSeller_id(seller_id);
        order.setTotal_amount(totalSum);
        order.setStatus(OrderStatus.ORDER_PLACED, usersBean.getId());
        AddressDTO del_address = createAddressDTOFromAddress(deliveryAddress);
        AddressDTO pickup_address = createAddressDTOFromAddress(sellerAddress);

        order.setPickup_address(pickup_address);
        order.setDelivery_address(del_address);
        order.setEstimated_delivery_charges(deliveryCharges);

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
        addressDTO.setLat(address.getLat());
        addressDTO.setLng(address.getLng());

        return addressDTO;
    }

    @NotNull
    private static Order_Item getOrderItem(Item item, Products product, String userId) {
        Order_Item order_Item = new Order_Item();
        order_Item.setProduct_id(product.getId());
        order_Item.setProduct_code(product.getProduct_code());
        order_Item.setProduct_name(product.getName());
        order_Item.setCdn_url(!CollectionUtils.isEmpty(product.getMedia()) ? product.getMedia().get(0).getCdn_url() : null);
        order_Item.setQuantity(item.getQuantity());
        order_Item.setSelling_price(product.getSelling_price());
        order_Item.setSeller_id(product.getSeller_id());
        order_Item.setSeller_code(product.getSeller_code());
        order_Item.setStatus(OrderStatus.ORDER_PLACED, userId);
        order_Item.setTotal_cost(product.getSelling_price() * item.getQuantity());
        if (item.is_secure()) {
            order_Item.setType(PurchaseType.SECURE);
        } else {
            order_Item.setType(PurchaseType.BUY);
        }
        return order_Item;
    }

}
