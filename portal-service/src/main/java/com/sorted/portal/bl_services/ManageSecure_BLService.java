package com.sorted.portal.bl_services;

import com.sorted.common.helper.SERequest;
import com.sorted.common.utils.CommonUtils;
import com.sorted.portal.request.beans.AppraiseSecureReturn;
import com.sorted.portal.request.beans.InitiateSecureBean;
import com.sorted.portal.request.beans.RescheduleSecureBean;
import com.sorted.portal.service.secure.SecureReturnService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling secure return related operations
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageSecure_BLService {

    private final SecureReturnService secureReturnService;

    /**
     * Initiates a secure return process for the given order items
     * 
     * @param request The HTTP request containing secure return details
     * @param httpServletRequest The servlet request for extracting headers
     */
    @PostMapping("/secure/initiate")
    public void initiateReturn(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("Received secure return initiation request");
        
        InitiateSecureBean secureBean = request.getGenericRequestDataObject(InitiateSecureBean.class);
        log.debug("Extracted secure return request data: orderId={}, returnDate={}, itemsCount={}", 
            secureBean.getOrderId(), secureBean.getReturnDate(), 
            secureBean.getOrderItemIds() != null ? secureBean.getOrderItemIds().size() : 0);
        
        // Extract headers and set them in the secure bean
        CommonUtils.extractHeaders(httpServletRequest, secureBean);
        
        // Delegate to service layer for processing
        secureReturnService.initiateSecureReturn(secureBean);
        
        log.info("Completed secure return initiation for order ID: {}", secureBean.getOrderId());
    }

    @PostMapping("/secure/appraise")
    public void appraiseSecureReturn(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("Received secure return secure/appraise request");

        AppraiseSecureReturn appraiseSecureReturn = request.getGenericRequestDataObject(AppraiseSecureReturn.class);
        log.debug("Extracted secure return secure/appraise request data: orderId={}, value={}, remark={}",
                appraiseSecureReturn.getOrderId(), appraiseSecureReturn.getAmount(), appraiseSecureReturn.getRemark());

        // Extract headers and set them in the secure bean
        CommonUtils.extractHeaders(httpServletRequest, appraiseSecureReturn);

        // Delegate to service layer for processing
        secureReturnService.appraiseSecureReturn(appraiseSecureReturn);

        log.info("Completed secure return accept/reject for order ID: {}", appraiseSecureReturn.getOrderId());
    }

    /**
     * Reschedules a secure return pickup
     * Maximum 2 reschedules allowed per order
     * 
     * @param request The HTTP request containing reschedule details
     * @param httpServletRequest The servlet request for extracting headers
     */
    @PostMapping("/secure/reschedule")
    public void reschedulePickup(@RequestBody RescheduleSecureBean request, HttpServletRequest httpServletRequest) {
        log.info("Received secure return reschedule request");
        
        log.debug("Extracted reschedule request data: orderId={}, newPickupDate={}, newTimeSlot={}",
                request.getOrderId(), request.getNewPickupDate(), request.getNewTimeSlot());
        
        // Extract headers and set them in the reschedule bean
        CommonUtils.extractHeaders(httpServletRequest, request);
        
        // Delegate to service layer for processing
        secureReturnService.rescheduleSecureReturn(request);
        
        log.info("Completed secure return reschedule for order ID: {}", request.getOrderId());
    }

}
