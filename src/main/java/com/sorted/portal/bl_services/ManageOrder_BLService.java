package com.sorted.portal.bl_services;

// Java standard imports

import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.request.beans.CreateDeliveryBean;
import com.sorted.portal.request.beans.FindOrderReqBean;
import com.sorted.portal.request.beans.OrderAcceptRejectRequest;
import com.sorted.portal.response.beans.FindOneOrderResBean;
import com.sorted.portal.service.order.OrderProcessingService;
import com.sorted.portal.service.order.OrderSearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

}
