package com.sorted.portal.bl_services;

import com.sorted.common.utils.CommonUtils;
import com.sorted.portal.request.beans.AppraiseSecureReturn;
import com.sorted.portal.request.beans.FindOrderReqBean;
import com.sorted.portal.request.beans.InitiateSecureBean;
import com.sorted.portal.response.beans.SecureOrderDetailsBean;
import com.sorted.portal.service.secure.SecureReturnService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for handling secure return related operations
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageSecure_BLService {

    private final SecureReturnService secureReturnService;

    /**
     * Searches and retrieves SECURE orders for customers
     *
     * @param req                The search request containing filter criteria
     * @param httpServletRequest The HTTP servlet request
     * @return A response containing a list of matching SECURE orders
     */
    @PostMapping("/secure/orders")
    public List<SecureOrderDetailsBean> findSecureOrders(@RequestBody FindOrderReqBean req, HttpServletRequest httpServletRequest) {
        return secureReturnService.findSecureOrders(req, httpServletRequest);
    }

    /**
     * Initiates a secure return process for the given order items
     *
     * @param request            The HTTP request containing secure return details
     * @param httpServletRequest The servlet request for extracting headers
     */
    @PostMapping("/secure/initiate")
    public void initiateReturn(@RequestBody InitiateSecureBean request, HttpServletRequest httpServletRequest) {
        log.info("Received secure return initiation request");

        // Extract headers and set them in the secure bean
        CommonUtils.extractHeaders(httpServletRequest, request);

        // Delegate to service layer for processing
        secureReturnService.initiateSecureReturn(request);

        log.info("Completed secure return initiation for order ID: {}", request.getOrderId());
    }

    @PostMapping("/secure/appraise")
    public void appraiseSecureReturn(@RequestBody AppraiseSecureReturn request, HttpServletRequest httpServletRequest) {
        log.info("Received secure return secure/appraise request");

        // Extract headers and set them in the secure bean
        CommonUtils.extractHeaders(httpServletRequest, request);

        // Delegate to service layer for processing
        secureReturnService.appraiseSecureReturn(request);

        log.info("products appraised for secure return: {}", request.getSecureReturnId());
    }

    /**
     * Reschedules a secure return pickup
     * Maximum 2 reschedules allowed per order
     *
     * @param request            The HTTP request containing reschedule details
     * @param httpServletRequest The servlet request for extracting headers
     */
    @PostMapping("/secure/reschedule")
    public void reschedulePickup(@RequestBody InitiateSecureBean request, HttpServletRequest httpServletRequest) {
        log.info("Received secure return reschedule request");

        log.debug("Extracted reschedule request data: orderId={}, newPickupDate={}, newTimeSlot={}",
                request.getSecureReturnId(), request.getReturnDate(), request.getTimeSlot());

        // Extract headers and set them in the reschedule bean
        CommonUtils.extractHeaders(httpServletRequest, request);

        // Delegate to service layer for processing
        secureReturnService.rescheduleSecureReturn(request);

        log.info("Completed secure return reschedule for order ID: {}", request.getOrderId());
    }

}
