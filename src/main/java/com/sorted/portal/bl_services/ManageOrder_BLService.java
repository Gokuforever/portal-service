package com.sorted.portal.bl_services;

// Java standard imports

import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.porter.req.beans.CreateOrderBean;
import com.sorted.commons.porter.req.beans.CreateOrderBean.*;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.enums.OrderItemsProperties;
import com.sorted.portal.enums.OrderProperties;
import com.sorted.portal.request.beans.CreateDeliveryBean;
import com.sorted.portal.request.beans.FindOrderReqBean;
import com.sorted.portal.request.beans.OrderAcceptRejectRequest;
import com.sorted.portal.response.beans.*;
import com.sorted.portal.service.FileGeneratorUtil;
import com.sorted.portal.service.order.OrderProcessingService;
import com.sorted.portal.service.order.OrderSearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller class that handles order management operations including status changes,
 * order creation, finding orders, and generating order reports.
 * <p>
 * This service integrates with Porter delivery service for order fulfillment
 * and provides CRUD operations for orders in the system.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageOrder_BLService {

    private final OrderProcessingService orderProcessingService;
    private final OrderSearchService orderSearchService;


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
        CreateDeliveryBean req = request.getGenericRequestDataObject(CreateDeliveryBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        return orderProcessingService.processReadyForPickup(req);
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
        // TODO: Implement secure return logic
        return null;
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
    public SEResponse acceptOrReject(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("acceptOrReject:: API started!");
        OrderAcceptRejectRequest req = request.getGenericRequestDataObject(OrderAcceptRejectRequest.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        return orderProcessingService.processAcceptReject(req);
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
        log.info("internalFind:: API started for order search");
        FindOrderReqBean req = request.getGenericRequestDataObject(FindOrderReqBean.class);
        return orderSearchService.findOrdersInternal(req, httpServletRequest);
    }

    /**
     * Searches and retrieves orders based on various criteria for customers.
     * Filters orders based on user permissions and provided search parameters.
     *
     * @param request            The search request containing filter criteria
     * @param httpServletRequest The HTTP servlet request
     * @return A response containing a list of matching orders
     */
    @PostMapping("/order/store/find")
    public SEResponse find(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("find:: API started for customer order search");
        FindOrderReqBean req = request.getGenericRequestDataObject(FindOrderReqBean.class);
        return orderSearchService.findOrdersForCustomer(req, httpServletRequest);
    }

    @PostMapping("/order/store/findOne")
    public FindOneOrderResBean findOne(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("findOne:: API started for customer order search");
        FindOrderReqBean req = request.getGenericRequestDataObject(FindOrderReqBean.class);
        return orderSearchService.findOne(req, httpServletRequest);
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
        FindOrderReqBean req = request.getGenericRequestDataObject(FindOrderReqBean.class);
        return orderSearchService.generateOrderReport(req, httpServletRequest);
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
                .product_name(products == null ? "" : products.getName()).cdn_url(products == null || products.getMedia() == null ? "" : products.getMedia().get(0).getCdn_url())
                .product_code(orderItem.getProduct_code()).quantity(orderItem.getQuantity()).total_cost(orderItem.getTotal_cost())
                .selling_price(orderItem.getSelling_price()).type(orderItem.getType()).status(orderItem.getStatus())
                .status_id(orderItem.getStatus_id())
                .build();
    }
}
