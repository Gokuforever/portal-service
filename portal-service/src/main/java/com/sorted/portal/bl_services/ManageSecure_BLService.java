package com.sorted.portal.bl_services;

import com.sorted.common.utils.CommonUtils;
import com.sorted.portal.request.beans.AppraiseSecureReturn;
import com.sorted.portal.request.beans.FindOrderReqBean;
import com.sorted.portal.request.beans.InitiateSecureBean;
import com.sorted.portal.response.beans.SecureOrderDetailsBean;
import com.sorted.portal.response.beans.SecurePickupDetailsBean;
import com.sorted.portal.service.secure.SecureReturnService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Gets secure pickup details by confirmation token.
     * Frontend uses this to display pickup details page with confirm button.
     *
     * @param token The confirmation token from the email link
     * @return SecurePickupDetailsBean with pickup details
     */
    @GetMapping("/secure/pickup-details")
    public SecurePickupDetailsBean getPickupDetails(@RequestParam String token) {
        log.info("Received pickup details request");
        return secureReturnService.getPickupDetailsByToken(token);
    }

    /**
     * Confirms pickup availability for a secure return.
     * Called when user clicks the confirm button on the pickup details page.
     *
     * @param token The confirmation token from the email link
     */
    @PostMapping("/secure/confirm-pickup")
    public void confirmPickup(@RequestParam String token) {
        log.info("Received pickup confirmation request");
        secureReturnService.confirmPickup(token);
        log.info("Pickup confirmed successfully");
    }

}
