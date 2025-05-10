package com.sorted.portal.bl_services;

// Java standard imports
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Spring framework imports
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Razorpay imports
import com.razorpay.RazorpayException;
import com.razorpay.Refund;

// JSON processing
import org.json.JSONObject;

// Lombok
import lombok.extern.slf4j.Slf4j;

// JetBrains annotations
import org.jetbrains.annotations.NotNull;

// Project-specific imports - Common utilities and beans
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.Preconditions;

// Project-specific imports - Entity and services
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;

// Project-specific imports - Enums
import com.sorted.commons.enums.*;
import com.sorted.portal.enums.OrderItemsProperties;
import com.sorted.portal.enums.OrderProperties;

// Project-specific imports - Exceptions
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;

// Project-specific imports - Helper classes
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.Pagination;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;

// Project-specific imports - Porter request/response beans
import com.sorted.commons.porter.req.beans.CreateOrderBean;
import com.sorted.commons.porter.req.beans.CreateOrderBean.Address;
import com.sorted.commons.porter.req.beans.CreateOrderBean.*;
import com.sorted.commons.porter.res.beans.CreateOrderResBean;
import com.sorted.commons.porter.res.beans.FetchOrderRes.FareDetails;

// Project-specific imports - Request/response beans
import com.sorted.portal.request.beans.CreateDeliveryBean;
import com.sorted.portal.request.beans.FindOrderReqBean;
import com.sorted.portal.request.beans.OrderAcceptRejectRequest;
import com.sorted.portal.response.beans.FindOrderResBean;
import com.sorted.portal.response.beans.OrderItemDTO;
import com.sorted.portal.response.beans.OrderItemReportsDTO;
import com.sorted.portal.response.beans.OrderReportDTO;

// Project-specific imports - Services
import com.sorted.portal.razorpay.RazorpayUtility;
import com.sorted.portal.service.ExcelGenerationUtility;
import com.sorted.portal.service.FileGeneratorUtil;

import static com.sorted.commons.enums.OrderStatus.*;

/**
 * Controller class that handles order management operations including status changes,
 * order creation, finding orders, and generating order reports.
 * <p>
 * This service integrates with Porter delivery service for order fulfillment
 * and provides CRUD operations for orders in the system.
 */
@Slf4j
@RestController
public class ManageOrder_BLService {

    //---------------------------------------------------------------------
    // Fields
    //---------------------------------------------------------------------
    private final Order_Details_Service order_Details_Service;
    private final Order_Item_Service order_Item_Service;
    private final ProductService productService;
    private final Users_Service users_Service;
    private final PorterUtility porterUtility;
    private final int defaultPage;
    private final int defaultSize;
    private final RazorpayUtility razorpayUtility;

    private final List<OrderStatus> sellerAllowedStatus = List.of(TRANSACTION_PROCESSED, ORDER_ACCEPTED, READY_FOR_PICK_UP, RIDER_ASSIGNED, OUT_FOR_DELIVERY, DELIVERED);

    //---------------------------------------------------------------------
    // Constructor
    //---------------------------------------------------------------------
    /**
     * Constructor for ManageOrder_BLService with all required dependencies.
     *
     * @param order_Details_Service Service for order details operations
     * @param order_Item_Service    Service for order item operations
     * @param productService        Service for product operations
     * @param users_Service         Service for user operations
     * @param porterUtility         Utility for Porter delivery service integration
     * @param defaultPage           Default page number for pagination
     * @param defaultSize           Default page size for pagination
     * @param razorpayUtility       Utility for Razorpay payment processing
     */
    public ManageOrder_BLService(
            Order_Details_Service order_Details_Service,
            Order_Item_Service order_Item_Service, 
            ProductService productService,
            Users_Service users_Service,
            PorterUtility porterUtility,
            @Value("${se.default.page}") int defaultPage,
            @Value("${se.default.size}") int defaultSize, 
            RazorpayUtility razorpayUtility) {
        this.order_Details_Service = order_Details_Service;
        this.order_Item_Service = order_Item_Service;
        this.productService = productService;
        this.users_Service = users_Service;
        this.porterUtility = porterUtility;
        this.defaultPage = defaultPage;
        this.defaultSize = defaultSize;
        this.razorpayUtility = razorpayUtility;
    }

    //---------------------------------------------------------------------
    // Order Status Management Endpoints
    //---------------------------------------------------------------------
    
    /**
     * Sets an order to "Ready for Pickup" status and creates a delivery order with Porter.
     * Only accessible to users with SELLER privileges.
     *
     * @param request            The request object containing the delivery information
     * @param httpServletRequest The HTTP servlet request
     * @return A response containing updated order details
     */
    @PostMapping("/readyForPickup")
    public SEResponse createOrder(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("createOrder (readyForPickup):: API started!");

        try {
            CreateDeliveryBean req = request.getGenericRequestDataObject(CreateDeliveryBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);

            log.debug("createOrder:: Validating user permissions for user ID: {}", req.getReq_user_id());
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
                    Activity.INVENTORY_MANAGEMENT);

            Role role = usersBean.getRole();
            UserType user_type = role.getUser_type();
            if (user_type != UserType.SELLER) {
                log.warn("createOrder:: Access denied for user type: {}", user_type);
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            if (!StringUtils.hasText(req.getOrder_id())) {
                log.warn("createOrder:: Order ID is mandatory");
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
            }

            log.debug("createOrder:: Fetching order details for order ID: {}", req.getOrder_id());
            SEFilter filterOD = new SEFilter(SEFilterType.AND);
            filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getOrder_id()));
            filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Order_Details order_Details = order_Details_Service.repoFindOne(filterOD);
            if (order_Details == null) {
                log.warn("createOrder:: No order found with ID: {}", req.getOrder_id());
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            if (order_Details.getStatus() != ORDER_ACCEPTED) {
                log.warn("createOrder:: Invalid order status: {} for order ID: {}",
                        order_Details.getStatus(), req.getOrder_id());
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
            }

            log.debug("createOrder:: Fetching order items for order ID: {}", req.getOrder_id());
            SEFilter filterOI = new SEFilter(SEFilterType.AND);
            filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, req.getOrder_id()));
            filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Order_Item> listOI = order_Item_Service.repoFind(filterOI);
            if (CollectionUtils.isEmpty(listOI)) {
                log.warn("createOrder:: No order items found for order ID: {}", req.getOrder_id());
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            List<String> seller_ids = listOI.stream().map(Order_Item::getSeller_id).distinct().toList();
            if (seller_ids.size() > 1) {
                log.warn("createOrder:: Multiple sellers found for order ID: {}", req.getOrder_id());
                // TODO: need to discuss multi-seller handling
            }
            String seller_id = seller_ids.get(0);

            Seller seller = usersBean.getSeller();
            if (!seller.getId().equals(seller_id)) {
                log.warn("createOrder:: Invalid seller for order. Expected: {}, Actual: {}",
                        seller_id, seller.getId());
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SELLER_FOR_ORDER);
            }

            log.debug("createOrder:: Validating primary SPOC for seller ID: {}", seller_id);
            Optional<Spoc_Details> primary_spoc = seller.getSpoc_details().stream()
                    .filter(Spoc_Details::isPrimary)
                    .findFirst();
            if (primary_spoc.isEmpty()) {
                log.warn("createOrder:: Missing primary SPOC for seller ID: {}", seller_id);
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRIMARY_SPOC);
            }

            Spoc_Details spoc_Details = primary_spoc.get();
            AddressDTO delivery_address = order_Details.getDelivery_address();
            AddressDTO pickup_address = order_Details.getPickup_address();
            String user_id = order_Details.getUser_id();

            log.debug("createOrder:: Fetching user details for user ID: {}", user_id);
            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, user_id));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Users users = users_Service.repoFindOne(filterU);
            if (users == null) {
                log.warn("createOrder:: User not found with ID: {}", user_id);
                // TODO: Handle missing user case
                throw new CustomIllegalArgumentsException(ResponseCode.INTERNAL_SERVER_ERROR);
            }

            log.debug("createOrder:: Preparing order creation request for Porter");
            CreateOrderBean order = getCreateOrderReq(order_Details, spoc_Details, delivery_address, pickup_address,
                    users);

            log.debug("createOrder:: Sending order creation request to Porter");
            CreateOrderResBean createOrderResBean = porterUtility.createOrder(order);
            String order_id = createOrderResBean.getOrder_id();

            log.debug("createOrder:: Updating order status to READY_FOR_PICK_UP for order ID: {}", req.getOrder_id());
            order_Details.setStatus(OrderStatus.READY_FOR_PICK_UP, usersBean.getId());
            order_Details.setDp_order_id(order_id);
            order_Details.setFare_details(FareDetails.builder()
                    .estimated_fare_details(createOrderResBean.getEstimated_fare_details()).build());
            order_Details.setEstimated_pickup_time(createOrderResBean.getEstimated_pickup_time());

            log.debug("createOrder:: Updating status for {} order items", listOI.size());
            listOI.forEach(e -> {
                e.setStatus(OrderStatus.READY_FOR_PICK_UP, usersBean.getId());
                order_Item_Service.update(e.getId(), e, usersBean.getId());
            });

            Order_Details details = order_Details_Service.update(order_Details.getId(), order_Details,
                    usersBean.getId());

            log.info("createOrder:: API completed successfully for order ID: {}", req.getOrder_id());
            return SEResponse.getBasicSuccessResponseObject(details, ResponseCode.READY_FOR_DISPATCH);

        } catch (CustomIllegalArgumentsException ex) {
            log.error("createOrder:: Validation error: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("createOrder:: Unexpected exception occurred", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    /**
     * Initiates a secure return process for an order.
     * Only accessible to users with SUPER_ADMIN privileges.
     *
     * @param request            The request object containing the delivery information
     * @param httpServletRequest The HTTP servlet request
     * @return A response indicating the success or failure of the operation
     */
    @PostMapping("/change/status/secure/return")
    public SEResponse initiateSecureReturn(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("initiateSecureReturn:: API started!");

        try {
            CreateDeliveryBean req = request.getGenericRequestDataObject(CreateDeliveryBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);

            log.debug("initiateSecureReturn:: Validating user permissions for user ID: {}", req.getReq_user_id());
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
                    Activity.SECURE_RETURN);

            Role role = usersBean.getRole();
            UserType user_type = role.getUser_type();

            if (user_type != UserType.SUPER_ADMIN) {
                log.warn("initiateSecureReturn:: Access denied for user type: {}", user_type);
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            if (!StringUtils.hasText(req.getOrder_id())) {
                log.warn("initiateSecureReturn:: Order ID is mandatory");
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
            }

            // TODO: Implement secure return logic
            log.info("initiateSecureReturn:: API completed successfully");
            return null;
        } catch (CustomIllegalArgumentsException ex) {
            log.error("initiateSecureReturn:: Validation error: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("initiateSecureReturn:: Unexpected exception occurred", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }
    
    /**
     * Accepts or rejects an order.
     * Only accessible to users with SELLER privileges.
     *
     * @param request            The request object containing accept/reject information
     * @param httpServletRequest The HTTP servlet request
     * @return A response indicating the success or failure of the operation
     */
    @PostMapping("/order/accept-or-reject")
    public SEResponse acceptOrReject(@RequestBody SERequest request, HttpServletRequest httpServletRequest) throws RazorpayException {

        OrderAcceptRejectRequest req = request.getGenericRequestDataObject(OrderAcceptRejectRequest.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        log.debug("report:: Validating user permissions for user ID: {}", req.getReq_user_id());
        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
                Permission.EDIT, Activity.INVENTORY_MANAGEMENT);
        // Apply user-specific filters based on role
        if (!usersBean.getRole().getUser_type().equals(UserType.SELLER)) {
            throw new AccessDeniedException();
        }
        Preconditions.check(StringUtils.hasText(req.getOrderId()), ResponseCode.MISSING_ORDER_ID);
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.eq(Order_Details.Fields.seller_id, usersBean.getSeller().getId()));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getOrderId()));

        Order_Details orderDetails = order_Details_Service.repoFindOne(filterOD);
        if (orderDetails == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        if (!orderDetails.getStatus().equals(TRANSACTION_PROCESSED)) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
        }

        if (req.isAccepted()) {
            orderDetails.setStatus(ORDER_ACCEPTED, usersBean.getId());
            order_Details_Service.update(orderDetails.getId(), orderDetails, usersBean.getId());
            //TODO: send notification mail and sms to seller
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        }

        orderDetails.setStatus(ORDER_REJECTED, usersBean.getId());
        orderDetails.setRejection_remarks(req.getRemark());
        order_Details_Service.update(orderDetails.getId(), orderDetails, usersBean.getId());

        Refund refund = razorpayUtility.refund(orderDetails.getTransaction_id(), orderDetails.getTotal_amount());
        if (refund == null) {
            orderDetails.setStatus(PENDING_REFUND, usersBean.getId());
            // TODO: initiate internal email
            // TODO: notify customer about rejection and pending refund request
            order_Details_Service.update(orderDetails.getId(), orderDetails, usersBean.getId());
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        }


        JSONObject json = refund.toJson();
        String status = json.get("status").toString();
        RazorpayRefundStatus refundStatus;
        try {
            refundStatus = RazorpayRefundStatus.valueOf(status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        switch (refundStatus) {
            case failed:
                // TODO: trigger email to studeaze informing about this failure
                orderDetails.setStatus(REFUND_FAILED, usersBean.getId());
                break;
            case pending:
                // TODO: trigger email and text message to customer informing about 5-7 business days.
                orderDetails.setStatus(REFUND_REQUESTED, usersBean.getId());
                orderDetails.setRefund_transaction_id(json.get("id").toString());
                break;
            case processed:
                // TODO: trigger email and text to customer informing refund is processed
                orderDetails.setStatus(FULLY_REFUNDED, usersBean.getId());
                orderDetails.setRefund_transaction_id(json.get("id").toString());
                break;
        }
        order_Details_Service.update(orderDetails.getId(), orderDetails, usersBean.getId());
        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    //---------------------------------------------------------------------
    // Order Search and Reporting Endpoints
    //---------------------------------------------------------------------
    
    /**
     * Searches and retrieves orders based on various criteria for internal use.
     * Filters orders based on user permissions and provided search parameters.
     *
     * @param request            The search request containing filter criteria
     * @param httpServletRequest The HTTP servlet request
     * @return A response containing a list of matching orders
     */
    @PostMapping("/order/find")
    public SEResponse internalFind(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("find:: API started for order search");
        log.debug("find:: Request: {}", request);

        try {
            FindOrderReqBean req = request.getGenericRequestDataObject(FindOrderReqBean.class);
            SEFilter filterOD = new SEFilter(SEFilterType.AND);
            CommonUtils.extractHeaders(httpServletRequest, req);

            log.debug("find:: Validating user permissions for user ID: {}", req.getReq_user_id());
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
                    Activity.ORDER_MANAGEMENT);

            // Apply user-specific filters based on role
            switch (usersBean.getRole().getUser_type()) {
                case SELLER:
                    log.debug("find:: Applying seller-specific filter for seller ID: {}",
                            usersBean.getSeller().getId());
                    filterOD.addClause(WhereClause.eq(Order_Details.Fields.seller_id, usersBean.getSeller().getId()));
                    if (StringUtils.hasText(req.getOrder_status())) {
                        OrderStatus orderStatus = OrderStatus.getByInternalStatus(req.getOrder_status());
                        if (orderStatus == null || !sellerAllowedStatus.contains(orderStatus)) {
                            log.warn("find:: Invalid status: {}", req.getOrder_status());
                            throw new IllegalArgumentException("Invalid status");
                        }
                        log.debug("find:: Applying status filter: {}", orderStatus);
                        filterOD.addClause(WhereClause.eq(Order_Details.Fields.status_id, orderStatus.getId()));
                    } else {
                        filterOD.addClause(WhereClause.in(Order_Details.Fields.status_id, sellerAllowedStatus.stream().map(OrderStatus::getId).toList()));
                    }
                    break;
                case SUPER_ADMIN:
                    log.debug("find:: User is SUPER_ADMIN, no seller-specific filter applied");
                    if (StringUtils.hasText(req.getOrder_status())) {
                        OrderStatus orderStatus = OrderStatus.getByInternalStatus(req.getOrder_status());
                        if (orderStatus == null) {
                            log.warn("find:: Invalid status: {}", req.getOrder_status());
                            throw new IllegalArgumentException("Invalid status");
                        }
                        log.debug("find:: Applying status filter: {}", orderStatus);
                        filterOD.addClause(WhereClause.eq(Order_Details.Fields.status_id, orderStatus.getId()));
                    }
                    break;
                default:
                    log.warn("find:: Access denied for user type: {}", usersBean.getRole().getUser_type());
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            // Apply optional filters based on request parameters
            if (StringUtils.hasText(req.getCode())) {
                log.debug("find:: Applying code filter: {}", req.getCode());
                filterOD.addClause(WhereClause.eq(Order_Details.Fields.code, req.getCode()));
            }


            if (StringUtils.hasText(req.getFrom_date()) && StringUtils.hasText(req.getTo_date())) {
                LocalDateTime from = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MIN);
                LocalDateTime to = LocalDate.parse(req.getTo_date()).atTime(LocalTime.MAX);
                log.debug("find:: Applying date range filter from {} to {}", from, to);
                filterOD.addClause(WhereClause.gte(BaseMongoEntity.Fields.creation_date, from));
                filterOD.addClause(WhereClause.lte(BaseMongoEntity.Fields.creation_date, to));
            }

            filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            // Set up ordering and pagination
            AggregationFilter.OrderBy orderBy = new AggregationFilter.OrderBy(
                    BaseMongoEntity.Fields.creation_date, AggregationFilter.SortOrder.DESC);
            filterOD.setOrderBy(orderBy);

            int page = req.getPage() < 0 ? defaultPage : req.getPage();
            int size = req.getSize() < 1 ? defaultSize : req.getSize();
            log.debug("find:: Using pagination - page: {}, size: {}", page, size);
            Pagination pagination = new Pagination(page, size);
            filterOD.setPagination(pagination);

            // Fetch orders
            log.debug("find:: Executing order search with filters");
            List<Order_Details> ordersList = order_Details_Service.repoFind(filterOD);
            if (CollectionUtils.isEmpty(ordersList)) {
                log.info("find:: No orders found matching the criteria");
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }
            log.debug("find:: Found {} orders matching the criteria", ordersList.size());

            // Fetch associated order items
            List<String> orderIds = ordersList.stream().map(BaseMongoEntity::getId).toList();
            SEFilter filterOI = new SEFilter(SEFilterType.AND);
            filterOI.addClause(WhereClause.in(Order_Item.Fields.order_id, orderIds));
            filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Order_Item> orderItems = order_Item_Service.repoFind(filterOI);
            Map<String, List<Order_Item>> mapOI = orderItems.stream()
                    .collect(Collectors.groupingBy(Order_Item::getOrder_id,
                            Collectors.mapping(Function.identity(), Collectors.toList())));

            List<String> productIds = orderItems.stream().map(Order_Item::getProduct_id).toList();

            SEFilter filterP = new SEFilter(SEFilterType.AND);
            filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));

            Map<String, Products> mapP = productService.repoFind(filterP).stream().collect(Collectors.toMap(Products::getId, products -> products));


            // Convert to response beans
            List<FindOrderResBean> resList = ordersList.stream()
                    .map(order -> this.entToBean(order, mapOI, mapP))
                    .toList();

            log.info("find:: API completed successfully, returning {} orders", resList.size());
            return SEResponse.getBasicSuccessResponseList(resList, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            log.error("find:: Validation error: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("find:: Unexpected exception occurred", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    /**
     * Searches and retrieves orders based on various criteria for customers.
     * Filters orders based on user permissions and provided search parameters.
     *
     * @param request            The search request containing filter criteria
     * @param httpServletRequest The HTTP servlet request
     * @return A response containing a list of matching orders
     */
    @PostMapping("/order/find")
    public SEResponse find(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("find:: API started for order search");
        log.debug("find:: Request: {}", request);

        try {
            FindOrderReqBean req = request.getGenericRequestDataObject(FindOrderReqBean.class);
            SEFilter filterOD = new SEFilter(SEFilterType.AND);
            CommonUtils.extractHeaders(httpServletRequest, req);

            log.debug("find:: Validating user permissions for user ID: {}", req.getReq_user_id());
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
                    Activity.ORDER_MANAGEMENT);

            // Apply user-specific filters based on role
            switch (usersBean.getRole().getUser_type()) {
                case CUSTOMER:
                    break;
                case GUEST:
                    log.info("No orders for guest.");
                    return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
                default:
                    log.warn("find:: Access denied for user type: {}", usersBean.getRole().getUser_type());
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            filterOD.addClause(WhereClause.eq(Order_Details.Fields.user_id, usersBean.getId()));
            if (StringUtils.hasText(req.getOrder_status())) {
                List<OrderStatus> orderStatuses = getByCustomerStatus(req.getOrder_status());
                log.debug("find:: Applying status filter: {}", orderStatuses);
                filterOD.addClause(WhereClause.in(Order_Details.Fields.status_id, orderStatuses.stream().map(OrderStatus::getId).toList()));
            }

            // Apply optional filters based on request parameters
            if (StringUtils.hasText(req.getCode())) {
                log.debug("find:: Applying code filter: {}", req.getCode());
                filterOD.addClause(WhereClause.eq(Order_Details.Fields.code, req.getCode()));
            }


            if (StringUtils.hasText(req.getFrom_date()) && StringUtils.hasText(req.getTo_date())) {
                LocalDateTime from = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MIN);
                LocalDateTime to = LocalDate.parse(req.getTo_date()).atTime(LocalTime.MAX);
                log.debug("find:: Applying date range filter from {} to {}", from, to);
                filterOD.addClause(WhereClause.gte(BaseMongoEntity.Fields.creation_date, from));
                filterOD.addClause(WhereClause.lte(BaseMongoEntity.Fields.creation_date, to));
            }

            filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            // Set up ordering and pagination
            AggregationFilter.OrderBy orderBy = new AggregationFilter.OrderBy(
                    BaseMongoEntity.Fields.creation_date, AggregationFilter.SortOrder.DESC);
            filterOD.setOrderBy(orderBy);

            int page = req.getPage() < 0 ? defaultPage : req.getPage();
            int size = req.getSize() < 1 ? defaultSize : req.getSize();
            log.debug("find:: Using pagination - page: {}, size: {}", page, size);
            Pagination pagination = new Pagination(page, size);
            filterOD.setPagination(pagination);

            // Fetch orders
            log.debug("find:: Executing order search with filters");
            List<Order_Details> ordersList = order_Details_Service.repoFind(filterOD);
            if (CollectionUtils.isEmpty(ordersList)) {
                log.info("find:: No orders found matching the criteria");
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }
            log.debug("find:: Found {} orders matching the criteria", ordersList.size());

            // Fetch associated order items
            List<String> orderIds = ordersList.stream().map(BaseMongoEntity::getId).toList();
            SEFilter filterOI = new SEFilter(SEFilterType.AND);
            filterOI.addClause(WhereClause.in(Order_Item.Fields.order_id, orderIds));
            filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Order_Item> orderItems = order_Item_Service.repoFind(filterOI);
            Map<String, List<Order_Item>> mapOI = orderItems.stream()
                    .collect(Collectors.groupingBy(Order_Item::getOrder_id,
                            Collectors.mapping(Function.identity(), Collectors.toList())));

            List<String> productIds = orderItems.stream().map(Order_Item::getProduct_id).toList();

            SEFilter filterP = new SEFilter(SEFilterType.AND);
            filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));

            Map<String, Products> mapP = productService.repoFind(filterP).stream().collect(Collectors.toMap(Products::getId, products -> products));


            // Convert to response beans
            List<FindOrderResBean> resList = ordersList.stream()
                    .map(order -> this.entToBeanForCustomer(order, mapOI, mapP))
                    .toList();

            log.info("find:: API completed successfully, returning {} orders", resList.size());
            return SEResponse.getBasicSuccessResponseList(resList, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            log.error("find:: Validation error: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("find:: Unexpected exception occurred", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    /**
     * Generates an Excel report of orders based on search criteria.
     * The report contains two sheets: one for orders and one for order items.
     *
     * @param request            The search request containing filter criteria
     * @param httpServletRequest The HTTP servlet request
     * @return A response containing the generated Excel file as bytes
     */
    @PostMapping("/order/report")
    public SEResponse report(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("report:: API started for order report generation");
        log.debug("report:: Request: {}", request);

        try {
            FindOrderReqBean req = request.getGenericRequestDataObject(FindOrderReqBean.class);
            SEFilter filterOD = new SEFilter(SEFilterType.AND);
            CommonUtils.extractHeaders(httpServletRequest, req);

            log.debug("report:: Validating user permissions for user ID: {}", req.getReq_user_id());
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
                    Activity.INVENTORY_MANAGEMENT);

            // Apply user-specific filters based on role
            switch (usersBean.getRole().getUser_type()) {
                case SELLER:
                    log.debug("report:: Applying seller-specific filter for seller ID: {}",
                            usersBean.getSeller().getId());
                    filterOD.addClause(WhereClause.eq(Order_Details.Fields.seller_id, usersBean.getSeller().getId()));
                    break;
                case SUPER_ADMIN:
                    log.debug("report:: User is SUPER_ADMIN, no seller-specific filter applied");
                    break;
                default:
                    log.warn("report:: Access denied for user type: {}", usersBean.getRole().getUser_type());
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            // Apply optional filters based on request parameters
            if (StringUtils.hasText(req.getCode())) {
                log.debug("report:: Applying code filter: {}", req.getCode());
                filterOD.addClause(WhereClause.eq(Order_Details.Fields.code, req.getCode()));
            }

            if (StringUtils.hasText(req.getOrder_status())) {
                OrderStatus orderStatus = OrderStatus.getByInternalStatus(req.getOrder_status());
                if (orderStatus == null) {
                    log.warn("report:: Invalid status: {}", req.getOrder_status());
                    throw new IllegalArgumentException("Invalid status");
                }
                log.debug("report:: Applying status filter: {}", orderStatus);
                filterOD.addClause(WhereClause.eq(Order_Details.Fields.status_id, orderStatus.getId()));
            }

            if (StringUtils.hasText(req.getFrom_date()) && StringUtils.hasText(req.getTo_date())) {
                LocalDateTime from = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MIN);
                LocalDateTime to = LocalDate.parse(req.getTo_date()).atTime(LocalTime.MAX);
                log.debug("report:: Applying date range filter from {} to {}", from, to);
                filterOD.addClause(WhereClause.gte(BaseMongoEntity.Fields.creation_date, from));
                filterOD.addClause(WhereClause.lte(BaseMongoEntity.Fields.creation_date, to));
            }

            filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            // Set up ordering and pagination
            AggregationFilter.OrderBy orderBy = new AggregationFilter.OrderBy(
                    BaseMongoEntity.Fields.creation_date, AggregationFilter.SortOrder.DESC);
            filterOD.setOrderBy(orderBy);

            int page = req.getPage() < 0 ? defaultPage : req.getPage();
            int size = req.getSize() < 1 ? defaultSize : req.getSize();
            log.debug("report:: Using pagination - page: {}, size: {}", page, size);
            Pagination pagination = new Pagination(page, size);
            filterOD.setPagination(pagination);

            // Fetch orders
            log.debug("report:: Executing order search with filters");
            List<Order_Details> ordersList = order_Details_Service.repoFind(filterOD);
            if (CollectionUtils.isEmpty(ordersList)) {
                log.info("report:: No orders found matching the criteria");
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }
            log.debug("report:: Found {} orders matching the criteria", ordersList.size());

            // Fetch associated order items
            List<String> orderIds = ordersList.stream().map(BaseMongoEntity::getId).toList();
            SEFilter filterOI = new SEFilter(SEFilterType.AND);
            filterOI.addClause(WhereClause.in(Order_Item.Fields.order_id, orderIds));
            filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Order_Item> orderItems = order_Item_Service.repoFind(filterOI);
            log.debug("report:: Found {} order items for the orders", orderItems.size());

            // Convert to report DTOs
            List<OrderReportDTO> orders = ordersList.stream().map(OrderReportDTO::new).toList();
            List<OrderItemReportsDTO> orderItemList = orderItems.stream().map(OrderItemReportsDTO::new).toList();

            // Generate Excel file
            log.debug("report:: Generating Excel file with order data");
            Map<String, FileGeneratorUtil.SheetConfig<?, ?>> sheetConfigMap = getSheetConfigMap(orders, orderItemList);
            byte[] bytes = ExcelGenerationUtility.createExcelFileInMemory(sheetConfigMap);

            log.info("report:: API completed successfully, generated Excel report with {} bytes", bytes.length);
            return SEResponse.getBasicSuccessResponseObject(bytes, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            log.error("report:: Validation error: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("report:: Unexpected exception occurred", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    //---------------------------------------------------------------------
    // Utility Methods
    //---------------------------------------------------------------------
    
    /**
     * Creates a Porter delivery request object from order details.
     *
     * @param order_Details    Order details
     * @param spoc_Details     Seller point of contact details
     * @param delivery_address Delivery address details
     * @param pickup_address   Pickup address details
     * @param users            User details
     * @return CreateOrderBean object for Porter API
     */
    private CreateOrderBean getCreateOrderReq(Order_Details order_Details, Spoc_Details spoc_Details,
                                              AddressDTO delivery_address, AddressDTO pickup_address, Users users) {
        log.debug("getCreateOrderReq:: Building Porter order request for order ID: {}", order_Details.getId());

        // Create delivery instructions
        Delivery_Instructions instruction1 = Delivery_Instructions.builder()
                .type("text")
                .description("handle with care")
                .build();

        Instruction_List instructionsList = Instruction_List.builder()
                .instructions_list(Collections.singletonList(instruction1))
                .build();

        // Create pickup details
        Address pickupAddress = Address.builder()
                .street_address1(pickup_address.getStreet_1())
                .street_address2(pickup_address.getStreet_2())
                .landmark(pickup_address.getLandmark())
                .city(pickup_address.getCity())
                .state(pickup_address.getState())
                .pincode(pickup_address.getPincode())
                .country("India")
                .lat(pickup_address.getLat())
                .lng(pickup_address.getLng())
                .contact_details(Contact_Details.builder()
                        .name(spoc_Details.getFirst_name() + " " + spoc_Details.getLast_name())
                        .phone_number("+91" + spoc_Details.getMobile_no())
                        .build())
                .build();

        Pickup_Details pickupDetails = Pickup_Details.builder()
                .address(pickupAddress)
                .build();

        // Create drop details
        Address dropAddress = Address.builder()
                .street_address1(delivery_address.getStreet_1())
                .street_address2(delivery_address.getStreet_2())
                .landmark(delivery_address.getLandmark())
                .city(delivery_address.getCity())
                .state(delivery_address.getState())
                .pincode(delivery_address.getPincode())
                .country("India")
                .lat(delivery_address.getLat())
                .lng(delivery_address.getLng())
                .contact_details(Contact_Details.builder()
                        .name(users.getFirst_name() + " " + users.getLast_name())
                        .phone_number("+91" + users.getMobile_no())
                        .build())
                .build();

        Drop_Details dropDetails = Drop_Details.builder()
                .address(dropAddress)
                .build();

        // Create the main order bean
        return CreateOrderBean.builder()
                .request_id(order_Details.getId())
                .delivery_instructions(instructionsList)
                .pickup_details(pickupDetails)
                .drop_details(dropDetails)
                .build();
    }
    
    /**
     * Creates sheet configuration for Excel report generation.
     *
     * @param orders        List of order report DTOs
     * @param orderItemList List of order item report DTOs
     * @return Map of sheet names to sheet configurations
     */
    @NotNull
    private static Map<String, FileGeneratorUtil.SheetConfig<?, ?>> getSheetConfigMap(
            List<OrderReportDTO> orders, List<OrderItemReportsDTO> orderItemList) {
        log.debug("getSheetConfigMap:: Creating sheet config for {} orders and {} order items",
                orders.size(), orderItemList.size());

        FileGeneratorUtil.SheetConfig<OrderReportDTO, OrderProperties> orderConfig =
                new FileGeneratorUtil.SheetConfig<>(orders, OrderProperties.class);
        FileGeneratorUtil.SheetConfig<OrderItemReportsDTO, OrderItemsProperties> orderItemsConfig =
                new FileGeneratorUtil.SheetConfig<>(orderItemList, OrderItemsProperties.class);

        Map<String, FileGeneratorUtil.SheetConfig<?, ?>> sheetConfigMap = new HashMap<>();
        sheetConfigMap.put("Orders", orderConfig);
        sheetConfigMap.put("Order Items", orderItemsConfig);
        return sheetConfigMap;
    }

    /**
     * Maps Order_Details entity to a FindOrderResBean for internal use.
     *
     * @param order_Details Order details entity
     * @param mapOI         Map of order items by order ID
     * @param mapP          Map of products by product ID
     * @return FindOrderResBean
     */
    private FindOrderResBean entToBean(Order_Details order_Details, Map<String, List<Order_Item>> mapOI, Map<String, Products> mapP) {
        return FindOrderResBean.builder().id(order_Details.getId()).code(order_Details.getCode())
                .status(order_Details.getStatus().getInternal_status())
                .transaction_id(order_Details.getTransaction_id()).total_amount(order_Details.getTotal_amount())
                .pickup_address(order_Details.getPickup_address()).delivery_address(order_Details.getDelivery_address())
                .orderItems(mapOI.get(order_Details.getId()).stream().map(orderItem -> this.entToBean(orderItem, mapP)).toList())
                .creation_date_str(order_Details.getCreation_date_str())
                .build();
    }

    /**
     * Maps Order_Details entity to a FindOrderResBean for customer use.
     *
     * @param order_Details Order details entity
     * @param mapOI         Map of order items by order ID
     * @param mapP          Map of products by product ID
     * @return FindOrderResBean
     */
    private FindOrderResBean entToBeanForCustomer(Order_Details order_Details, Map<String, List<Order_Item>> mapOI, Map<String, Products> mapP) {
        return FindOrderResBean.builder().id(order_Details.getId()).code(order_Details.getCode())
                .status(order_Details.getStatus().getCustomer_status())
                .total_amount(order_Details.getTotal_amount())
                .orderItems(mapOI.get(order_Details.getId()).stream().map(orderItem -> this.entToBean(orderItem, mapP)).toList())
                .creation_date_str(order_Details.getCreation_date_str())
                .build();
    }

    /**
     * Maps Order_Item entity to an OrderItemDTO.
     *
     * @param orderItem Order item entity
     * @param mapP      Map of products by product ID
     * @return OrderItemDTO
     */
    private OrderItemDTO entToBean(Order_Item orderItem, Map<String, Products> mapP) {
        Products products = mapP.getOrDefault(orderItem.getProduct_id(), null);
        return OrderItemDTO.builder().id(orderItem.getId()).product_id(orderItem.getProduct_id())
                .product_name(products == null ? "" : products.getName()).product_image(products == null || products.getMedia() == null ? "" : products.getMedia().get(0).getKey())
                .product_code(orderItem.getProduct_code()).quantity(orderItem.getQuantity()).total_cost(orderItem.getTotal_cost())
                .selling_price(orderItem.getSelling_price()).type(orderItem.getType()).status(orderItem.getStatus())
                .status_id(orderItem.getStatus_id())
                .build();
    }
}
