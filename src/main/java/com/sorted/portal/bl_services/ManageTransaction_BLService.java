package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.phonepe.sdk.pg.payments.v2.models.response.StandardCheckoutPayResponse;
import com.razorpay.RazorpayException;
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
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.porter.res.beans.GetQuoteResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.GsonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.razorpay.RazorpayUtility;
import com.sorted.portal.request.beans.PayNowBean;
import com.sorted.portal.response.beans.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final Order_Item_Service order_Item_Service;
    private final RazorpayUtility razorpayUtility;
    private final Seller_Service seller_Service;
    private final Order_Dump_Service orderDumpService;
    private final OrderService orderService;
    private final PhonePeUtility phonePeUtility;
    private final OrderStatusCheckService orderStatusCheckService;
    private final PorterUtility porterUtility;

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
            if (minCartValueInPaise <= totalSum) {
                if (cart.getDelivery_charges() == null || cart.getDelivery_charges() <= 1) {

                    GetQuoteResponse quote = porterUtility.getEstimateDeliveryAmount(address.getId(), seller.getAddress_id(), usersBean.getMobile_no(), usersBean.getFirst_name() + " " + usersBean.getLast_name());
                    if (quote == null) {
                        throw new CustomIllegalArgumentsException(ResponseCode.NOT_DELIVERIBLE);
                    }
                    cart.setDelivery_charges(quote.getVehicle().getFare().getMinor_amount());
                    cart_Service.update(cart.getId(), cart, usersBean.getId());
                }
                totalSum += cart.getDelivery_charges();
            }
            if (minCartValueInPaise <= totalSum && cart.getDelivery_charges() != null && cart.getDelivery_charges() > 0) {
                totalSum += cart.getDelivery_charges();
            }

            // Create order
            Order_Details order = createOrder(usersBean, seller.getId(), totalSum, address, sellerAddress);

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
        if(nearestSeller == null) {
            NearestSellerRes nearestSellerRes = porterUtility.getNearestSeller(address.getPincode(), usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getLast_name());
            nearestSeller = nearestSellerRes.getSeller_id();
            Users users = users_Service.findById(usersBean.getId()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.NO_RECORD));
            users.setNearestSeller(nearestSeller);
            users_Service.update(users.getId(), users, Defaults.SYSTEM_ADMIN);
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

    private LocalDateTime validateCartItems(List<Item> cart_items, PayNowBean req) {
        LocalDateTime return_date = null;
        boolean has_secure = cart_items.stream().anyMatch(Item::is_secure);
        if (has_secure) {
            if (!StringUtils.hasText(req.getReturn_date())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_RETURN_DATE);
            }
            try {
                return_date = LocalDate.parse(req.getReturn_date()).atTime(LocalTime.MAX);
            } catch (Exception e) {
                log.error("pay:: Exception occurred:: {}", e.getMessage());
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_RETURN_DATE);
            }
        }
        return return_date;
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

    private Order_Details createOrder(UsersBean usersBean, String seller_id, long totalSum, Address deliveryAddress, Address sellerAddress) {
        Order_Details order = new Order_Details();

        if (StringUtils.hasText(usersBean.getId())) {
            order.setUser_id(usersBean.getId());
        }
        order.setSeller_id(seller_id);
        order.setTotal_amount(totalSum);
        order.setStatus(OrderStatus.ORDER_PLACED, usersBean.getId());
        Order_Status_History order_history = Order_Status_History.builder().status(OrderStatus.ORDER_PLACED)
                .modification_date(LocalDateTime.now()).modified_by(usersBean.getId()).build();
        order.setOrder_status_history(Collections.singletonList(order_history));

        AddressDTO del_address = createAddressDTOFromAddress(deliveryAddress);
        AddressDTO pickup_address = createAddressDTOFromAddress(sellerAddress);

        order.setPickup_address(pickup_address);
        order.setDelivery_address(del_address);

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

    @PostMapping("/saveResponse")
    public SEResponse saveResponse(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("/saveResponse:: API started!");
            PGResponseBean req = request.getGenericRequestDataObject(PGResponseBean.class);

            // Validate request parameters
            validatePGResponse(req);

            // Find order details
            Order_Details order_details = findOrderDetailsByPgOrderId(req.getRazorpay_order_id());

            // Find order items
            List<Order_Item> items = findOrderItems(order_details.getId());

            // Extract product IDs
            List<String> product_ids = items.stream().map(Order_Item::getProduct_id).toList();

            // Determine order status and process payment
            OrderStatus orderStatus = processPaymentVerification(req, order_details, product_ids);

            // Update order items status
            updateOrderItemsStatus(items, orderStatus);

            // Update order status
            order_details.setStatus(orderStatus, Defaults.SYSTEM_ADMIN);
            order_Details_Service.update(order_details.getId(), order_details, Defaults.SYSTEM_ADMIN);

            log.info("/saveResponse:: API ended!");
            return SEResponse.getEmptySuccessResponse(ResponseCode.ORDER_PLACED);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/saveResponse:: exception occurred");
            log.error("/saveResponse:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private void validatePGResponse(PGResponseBean req) {
        if (!StringUtils.hasText(req.getRazorpay_order_id()) ||
                !StringUtils.hasText(req.getRazorpay_payment_id()) ||
                !StringUtils.hasText(req.getRazorpay_signature())) {
            throw new CustomIllegalArgumentsException(ResponseCode.PG_BAD_REQ);
        }
    }

    private Order_Details findOrderDetailsByPgOrderId(String pgOrderId) {
        SEFilter filterO = new SEFilter(SEFilterType.AND);
        filterO.addClause(WhereClause.eq(Order_Details.Fields.pg_order_id, pgOrderId));
        filterO.addClause(WhereClause.eq(Order_Details.Fields.status, OrderStatus.ORDER_PLACED.name()));
        filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Order_Details order_details = order_Details_Service.repoFindOne(filterO);
        if (order_details == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }
        return order_details;
    }

    private List<Order_Item> findOrderItems(String orderId) {
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.eq(Order_Item.Fields.order_id, orderId));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> items = order_Item_Service.repoFind(filterOD);
        if (CollectionUtils.isEmpty(items)) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }
        return items;
    }

    private OrderStatus processPaymentVerification(PGResponseBean req, Order_Details order_details, List<String> product_ids) throws RazorpayException {
        OrderStatus orderStatus;

        if (!StringUtils.hasText(req.getRazorpay_payment_id())) {
            orderStatus = OrderStatus.TRANSACTION_FAILED;
            // Note: Consider increasing product quantity for failed transactions
        } else {
            boolean verified = razorpayUtility.verifySignature(
                    req.getRazorpay_order_id(),
                    req.getRazorpay_payment_id(),
                    req.getRazorpay_signature()
            );

            if (!verified) {
                throw new CustomIllegalArgumentsException(ResponseCode.UNTRUSTED_RESPONSE);
            }

            // Update cart by removing purchased items
            updateCartAfterSuccessfulPayment(order_details.getUser_id(), product_ids);

            // Set transaction ID and update status
            order_details.setTransaction_id(req.getRazorpay_payment_id());
            orderStatus = OrderStatus.TRANSACTION_PROCESSED;
        }

        return orderStatus;
    }

    private void updateCartAfterSuccessfulPayment(String userId, List<String> product_ids) {
        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(Cart.Fields.user_id, userId));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Cart cart = cart_Service.repoFindOne(filterC);
        if (cart == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        List<Item> remainingItems = cart.getCart_items().stream()
                .filter(e -> !product_ids.contains(e.getProduct_id()))
                .toList();

        if (!CollectionUtils.isEmpty(remainingItems)) {
            cart.setCart_items(remainingItems);
        } else {
            cart.setCart_items(null);
        }

        cart_Service.update(cart.getId(), cart, Defaults.SYSTEM_ADMIN);
    }

    private void updateOrderItemsStatus(List<Order_Item> items, OrderStatus orderStatus) {
        for (Order_Item item : items) {
            item.setStatus(orderStatus, Defaults.SYSTEM_ADMIN);
            order_Item_Service.update(item.getId(), item, Defaults.SYSTEM_ADMIN);
        }
    }
}
