package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.phonepe.sdk.pg.payments.v2.models.response.StandardCheckoutPayResponse;
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Item;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
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

    @Value("${se.fixed-delivery-charge.in-paise:5900}")
    private long fixedDeliveryCharge;

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
            log.info("pay:: API started for request: {}", request);
            PayNowBean req = request.getGenericRequestDataObject(PayNowBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);

            log.info("pay:: Processing payment request for user: {}, delivery address: {}",
                    req.getReq_user_id(), req.getDelivery_address_id());

            // Validate user
            log.debug("pay:: Starting user validation");
            UsersBean usersBean = validateUserForPayment(req);
            log.info("pay:: User validation successful for user: {}, type: {}",
                    usersBean.getId(), usersBean.getRole().getUser_type());

            // Validate delivery address
            log.debug("pay:: Starting delivery address validation");
            Address address = validateDeliveryAddress(req, usersBean);
            log.info("pay:: Delivery address validation successful. Address ID: {}, Pincode: {}",
                    address.getId(), address.getPincode());

            // Validate cart
            log.debug("pay:: Starting cart validation");
            Cart cart = validateCart(usersBean);
            log.info("pay:: Cart validation successful. Cart ID: {}, Items count: {}",
                    cart.getId(), cart.getCart_items() != null ? cart.getCart_items().size() : 0);

            // Process cart items
            List<Item> cart_items = cart.getCart_items();
            log.debug("pay:: Processing {} cart items", cart_items.size());

            // Get products
            log.debug("pay:: Fetching products for cart items");
            List<Products> listP = getProductsForCart(cart_items);
            log.info("pay:: Retrieved {} products for cart", listP.size());

            // Validate seller
            log.debug("pay:: Starting seller validation");
            Seller seller = validateSeller(listP);
            log.info("pay:: Seller validation successful. Seller ID: {}", seller.getId());

            // Get seller address
            log.debug("pay:: Fetching seller address");
            Address sellerAddress = getSellerAddress(seller);
            log.info("pay:: Seller address retrieved. Address ID: {}, Pincode: {}",
                    sellerAddress.getId(), sellerAddress.getPincode());

            // Create order items
            log.debug("pay:: Creating order items");
            Map<String, Products> mapP = listP.stream().collect(Collectors.toMap(BaseMongoEntity::getId, e -> e));
            List<Order_Item> listOI = createOrderItems(cart_items, mapP, usersBean.getId());
            log.info("pay:: Created {} order items", listOI.size());

            // Calculate total
            log.debug("pay:: Calculating order totals");
            Map<String, Long> mapSecurePQ = listOI.stream().filter(e -> e.getType() == PurchaseType.SECURE)
                    .collect(Collectors.toMap(Order_Item::getProduct_id, Order_Item::getQuantity));
            Map<String, Long> mapDirectPQ = listOI.stream().filter(e -> e.getType() == PurchaseType.BUY)
                    .collect(Collectors.toMap(Order_Item::getProduct_id, Order_Item::getQuantity));

            long totalSum = listOI.stream().mapToLong(Order_Item::getTotal_cost).sum();
            log.info("pay:: Initial cart total calculated: {} paise", totalSum);

            if (totalSum < 1) {
                log.error("pay:: Invalid amount calculated: {}", totalSum);
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_AMOUNT);
            }

            long deliveryCharge = minCartValueInPaise >= totalSum ? fixedDeliveryCharge : 0;
            cart.setDelivery_charges(deliveryCharge);
            cart_Service.update(cart.getId(), cart, usersBean.getId());
            // Handle delivery charges
//            if (minCartValueInPaise >= totalSum) {
//                log.info("pay:: Cart value {} is below minimum {}, checking delivery charges",
//                        totalSum, minCartValueInPaise);
//
//                if (cart.getDelivery_charges() == null || cart.getDelivery_charges() <= 0) {
//                    log.debug("pay:: Fetching delivery quote from Porter");
//                    GetQuoteResponse quote = porterUtility.getEstimateDeliveryAmount(
//                            address.getId(), seller.getAddress_id(),
//                            usersBean.getFirst_name() + " " + usersBean.getLast_name());
//
//                    if (quote == null) {
//                        log.warn("pay:: No delivery quote available for pincode: {}", address.getPincode());
//                        demandingPincodeService.storeDemandingPincode(address.getPincode(), usersBean.getId());
//                        throw new CustomIllegalArgumentsException(ResponseCode.NOT_DELIVERIBLE);
//                    }
//
//                    deliveryCharge = quote.getVehicle().getFare().getMinor_amount();
//                    log.info("pay:: Delivery charges calculated: {} paise", deliveryCharge);
//
//                    cart.setDelivery_charges(deliveryCharge);
//                    cart_Service.update(cart.getId(), cart, usersBean.getId());
//                    log.debug("pay:: Cart updated with delivery charges");
//                }
//
//                totalSum += cart.getDelivery_charges();
//                log.info("pay:: Final total with delivery charges: {} paise", totalSum);
//            } else {
//                log.info("pay:: Cart value {} meets minimum threshold, no delivery charges needed", totalSum);
//            }

            // Create order
            totalSum = totalSum + deliveryCharge;

            log.debug("pay:: Creating order entity");
            Order_Details order = createOrder(usersBean, seller.getId(), totalSum, address, sellerAddress,
                    deliveryCharge);
            log.info("pay:: Order entity created with total amount: {} paise", totalSum);

            // Reduce product quantity
            log.debug("pay:: Reducing product quantities");
            orderService.reduceProductQuantity(mapP.values().stream().toList(), mapSecurePQ);
            orderService.reduceProductQuantity(mapP.values().stream().toList(), mapDirectPQ);
            log.info("pay:: Product quantities reduced successfully");

            // Reduce cart quantity
            log.debug("pay:: Emptying user cart");
            orderService.emptyCart(cart.getId(), usersBean.getId());
            log.info("pay:: Cart emptied successfully");

            // Save order
            log.debug("pay:: Persisting order to database");
            Order_Details order_Details = order_Details_Service.create(order, usersBean.getId());
            log.info("pay:: Order saved successfully with ID: {}, Code: {}",
                    order_Details.getId(), order_Details.getCode());

            // Create order items in DB
            log.debug("pay:: Creating order items in database");
            orderService.createOrderItems(listOI, order_Details.getId(), order_Details.getCode(), usersBean.getId());
            log.info("pay:: Order items created successfully for order: {}", order_Details.getId());

            // Create payment order
            log.debug("pay:: Creating PhonePe payment order");
            Optional<StandardCheckoutPayResponse> checkoutPayResponseOptional =
                    phonePeUtility.createOrder(order_Details.getId(), totalSum);

            if (checkoutPayResponseOptional.isEmpty()) {
                log.error("pay:: Failed to create PhonePe order for order ID: {}, amount: {}",
                        order_Details.getId(), totalSum);
                throw new CustomIllegalArgumentsException(ResponseCode.PG_ORDER_GEN_FAILED);
            }

            // Update order with payment details
            StandardCheckoutPayResponse checkoutPayResponse = checkoutPayResponseOptional.get();
            String pgOrderId = checkoutPayResponse.getOrderId();
            log.info("pay:: PhonePe order created successfully. PG Order ID: {}", pgOrderId);

            order_Details.setPg_order_id(pgOrderId);
            order_Details_Service.update(order_Details.getId(), order_Details, usersBean.getId());
            log.debug("pay:: Order updated with PG order ID");

            // Build response
            String redirectUrl = checkoutPayResponse.getRedirectUrl();
            String orderId = order_Details.getId();

            log.info("pay:: Payment process completed successfully. Order ID: {}, PG Order ID: {}",
                    orderId, pgOrderId);

            PayNowResponse payNowResponse = PayNowResponse.builder()
                    .redirectUrl(redirectUrl)
                    .orderId(orderId)
                    .build();

            orderDumpService.markSuccess(orderDump, order_Details.getId(), this.getClass().getSimpleName());
            log.info("pay:: API completed successfully for order: {}", orderId);

            return payNowResponse;

        } catch (CustomIllegalArgumentsException ex) {
            log.error("pay:: Custom exception occurred - Code: {}, Message: {}",
                    ex.getResponseCode().getCode(), ex.getResponseCode().getErrorMessage());
            orderDumpService.markFailed(orderDump, ex.getResponseCode().getErrorMessage(),
                    ex.getResponseCode().getCode(), this.getClass().getSimpleName());
            throw ex;
        } catch (Exception e) {
            log.error("pay:: Unexpected exception occurred: {}", e.getMessage(), e);
            orderDumpService.markFailed(orderDump, e.getMessage(), ResponseCode.ERR_0001.getCode(),
                    this.getClass().getSimpleName());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    // Enhanced validation methods with logging
    private UsersBean validateUserForPayment(PayNowBean req) {
        log.debug("validateUserForPayment:: Validating user: {}", req.getReq_user_id());

        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PURCHASE);

        switch (usersBean.getRole().getUser_type()) {
            case CUSTOMER -> {
                log.debug("validateUserForPayment:: Valid customer user type");
            }
            case GUEST -> {
                log.warn("validateUserForPayment:: Guest user attempted payment: {}", req.getReq_user_id());
                throw new CustomIllegalArgumentsException(ResponseCode.PROMT_SIGNUP);
            }
            default -> {
                log.warn("validateUserForPayment:: Invalid user type {} for payment",
                        usersBean.getRole().getUser_type());
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
        }
        return usersBean;
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

        log.debug("validateDeliveryAddress:: Address found, checking nearest seller");
        String nearestSeller = usersBean.getNearestSeller();

//        if (nearestSeller == null) {
//            log.info("validateDeliveryAddress:: Finding nearest seller for pincode: {}", address.getPincode());
//            NearestSellerRes nearestSellerRes = porterUtility.getNearestSeller(
//                    address.getPincode(), usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getId());
//
//            nearestSeller = nearestSellerRes.getSeller_id();
//            log.info("validateDeliveryAddress:: Nearest seller found: {}", nearestSeller);
//
//            Users users = users_Service.findById(usersBean.getId())
//                    .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.NO_RECORD));
//            users.setNearestSeller(nearestSeller);
//            users_Service.update(users.getId(), users, Defaults.SYSTEM_ADMIN);
//            usersBean.setNearestSeller(nearestSeller);
//            log.debug("validateDeliveryAddress:: User updated with nearest seller");
//        } else {
//            log.debug("validateDeliveryAddress:: Using existing nearest seller: {}", nearestSeller);
//        }

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

    private List<Products> getProductsForCart(List<Item> cart_items) {
        Set<String> product_ids = cart_items.stream().map(Item::getProduct_id).collect(Collectors.toSet());
        log.debug("getProductsForCart:: Fetching {} products", product_ids.size());

        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(product_ids)));
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Products> listP = productService.repoFind(filterP);
        if (CollectionUtils.isEmpty(listP)) {
            log.error("getProductsForCart:: No products found for cart items");
            throw new CustomIllegalArgumentsException(ResponseCode.OUT_OF_STOCK);
        }

        log.debug("getProductsForCart:: Retrieved {} products successfully", listP.size());
        return listP;
    }

    private Seller validateSeller(List<Products> listP) {
        List<String> seller_ids = listP.stream().map(Products::getSeller_id).distinct().toList();
        log.debug("validateSeller:: Validating sellers: {}", seller_ids);

        if (seller_ids.isEmpty()) {
            log.error("validateSeller:: No seller IDs found in products");
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SELLER_FOR_ORDER);
        }

        String seller_id = seller_ids.get(0);
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

    private List<Order_Item> createOrderItems(List<Item> cart_items, Map<String, Products> mapP, String userId) {
        log.debug("createOrderItems:: Creating order items for {} cart items", cart_items.size());
        List<Order_Item> listOI = new ArrayList<>();

        for (Item item : cart_items) {
            if (!mapP.containsKey(item.getProduct_id())) {
                log.error("createOrderItems:: Product not found in map: {}", item.getProduct_id());
                throw new CustomIllegalArgumentsException(ResponseCode.DELETED_PRODUCT);
            }

            Products product = mapP.get(item.getProduct_id());
            if (product == null) {
                log.warn("createOrderItems:: Skipping null product: {}", item.getProduct_id());
                continue;
            }

            if (product.getQuantity().compareTo(item.getQuantity()) < 0) {
                log.error("createOrderItems:: Insufficient stock for product: {}. Available: {}, Requested: {}",
                        product.getId(), product.getQuantity(), item.getQuantity());
                throw new CustomIllegalArgumentsException(ResponseCode.FEW_OUT_OF_STOCK);
            }

            Order_Item order_Item = getOrderItem(item, product, userId);
            listOI.add(order_Item);
            log.debug("createOrderItems:: Created order item for product: {}, quantity: {}, total: {}",
                    product.getId(), item.getQuantity(), order_Item.getTotal_cost());
        }

        log.info("createOrderItems:: Successfully created {} order items", listOI.size());
        return listOI;
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
        order.setDelivery_charges(deliveryCharges);
        order.setTotal_items_cost(totalSum - deliveryCharges);
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
        if (StringUtils.hasText(address.getPhone_no())) {
            addressDTO.setPhone_no(address.getPhone_no());
        }
        addressDTO.setLat(address.getLat());
        addressDTO.setLng(address.getLng());
        addressDTO.setFirst_name(address.getFirstName());
        addressDTO.setLast_name(address.getLastName());


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
