//package com.sorted.portal.bl_services;
//
//import com.sorted.common.helper.SERequest;
//import com.sorted.common.helper.SEResponse;
//import com.sorted.common.utils.CommonUtils;
//import com.sorted.portal.request.beans.AppraisalRequestDTO;
//import com.sorted.portal.request.beans.InitiateSecureBean;
//import com.sorted.portal.request.beans.RescheduleSecureBean;
//import com.sorted.portal.response.beans.SecureReturnDTO;
//import com.sorted.portal.service.secure.SecureReturnServiceV2;
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
///**
// * V2 Controller for Secure Buy/Return operations using new Secure_Return entity
// *
// * All endpoints are prefixed with /v2/secure
// */
//@Slf4j
//@RestController
//@RequestMapping("/v2/secure")
//@RequiredArgsConstructor
//public class ManageSecure_BLService_V2 {
//
//    private final SecureReturnServiceV2 secureReturnServiceV2;
//
//    /**
//     * V2: Initiate a secure return
//     *
//     * POST /v2/secure/initiate
//     *
//     * Request Body:
//     * {
//     *   "order_id": "order123",
//     *   "order_item_ids": ["item1", "item2"],
//     *   "return_date": "2026-03-05",
//     *   "time_slot": "MORNING",
//     *   "address_id": "addr123"
//     * }
//     *
//     * Response: SecureReturnDTO with complete secure return details
//     */
//    @PostMapping("/initiate")
//    public SecureReturnDTO initiateReturn(
//            @RequestBody InitiateSecureBean request,
//            HttpServletRequest httpServletRequest
//    ) {
//        log.info("V2: Received secure return initiation request");
//
//        log.debug("V2: Extracted secure return request - orderId={}, returnDate={}, itemsCount={}",
//                request.getOrderId(), request.getReturnDate(),
//                request.getOrderItemIds() != null ? request.getOrderItemIds().size() : 0);
//
//        // Extract headers and set them in the secure bean
//        CommonUtils.extractHeaders(httpServletRequest, request);
//
//        // Delegate to service layer
//        SecureReturnDTO result = secureReturnServiceV2.initiateSecureReturn(request);
//
//        log.info("V2: Successfully initiated secure return: {}", result.getId());
//
//        return SEResponse.getSuccessResponse(result, "Secure return initiated successfully");
//    }
//
//    /**
//     * V2: Appraise returned items (Seller only)
//     *
//     * POST /v2/secure/appraise
//     *
//     * Request Body:
//     * {
//     *   "secure_return_id": "sec_ret_123",
//     *   "item_appraisals": [
//     *     {
//     *       "order_item_id": "item1",
//     *       "grade": "A",
//     *       "remarks": "Excellent condition",
//     *       "image_urls": ["https://s3.../img1.jpg", "https://s3.../img2.jpg"]
//     *     },
//     *     {
//     *       "order_item_id": "item2",
//     *       "grade": "B",
//     *       "remarks": "Minor scratches",
//     *       "image_urls": ["https://s3.../img3.jpg"]
//     *     }
//     *   ]
//     * }
//     *
//     * Response: SecureReturnDTO with appraisal details and calculated refunds
//     */
//    @PostMapping("/appraise")
//    public SEResponse<SecureReturnDTO> appraiseSecureReturn(
//            @RequestBody SERequest request,
//            HttpServletRequest httpServletRequest
//    ) {
//        log.info("V2: Received secure return appraisal request");
//
//        AppraisalRequestDTO appraisalRequest = request.getGenericRequestDataObject(AppraisalRequestDTO.class);
//        log.debug("V2: Extracted appraisal request - secureReturnId={}, itemsCount={}",
//                appraisalRequest.getSecure_return_id(),
//                appraisalRequest.getItem_appraisals() != null ? appraisalRequest.getItem_appraisals().size() : 0);
//
//        // Extract headers
//        CommonUtils.extractHeaders(httpServletRequest, appraisalRequest);
//
//        // Delegate to service layer
//        SecureReturnDTO result = secureReturnServiceV2.appraiseSecureReturn(appraisalRequest);
//
//        log.info("V2: Successfully appraised secure return: {}", result.getId());
//
//        return SEResponse.getSuccessResponse(result, "Items appraised successfully");
//    }
//
//    /**
//     * V2: Reschedule secure return pickup
//     *
//     * POST /v2/secure/reschedule
//     *
//     * Request Body:
//     * {
//     *   "order_id": "order123",
//     *   "new_pickup_date": "2026-03-07",
//     *   "new_time_slot": "AFTERNOON",
//     *   "address_id": "addr456" // Optional - to change pickup address
//     * }
//     *
//     * Response: SecureReturnDTO with updated schedule
//     */
//    @PostMapping("/reschedule")
//    public SEResponse<SecureReturnDTO> reschedulePickup(
//            @RequestBody SERequest request,
//            HttpServletRequest httpServletRequest
//    ) {
//        log.info("V2: Received secure return reschedule request");
//
//        RescheduleSecureBean rescheduleBean = request.getGenericRequestDataObject(RescheduleSecureBean.class);
//        log.debug("V2: Extracted reschedule request - orderId={}, newDate={}, newTimeSlot={}",
//                rescheduleBean.getOrderId(), rescheduleBean.getNewPickupDate(), rescheduleBean.getNewTimeSlot());
//
//        // Extract headers
//        CommonUtils.extractHeaders(httpServletRequest, rescheduleBean);
//
//        // Delegate to service layer
//        SecureReturnDTO result = secureReturnServiceV2.rescheduleSecureReturn(rescheduleBean);
//
//        log.info("V2: Successfully rescheduled secure return: {}", result.getId());
//
//        return SEResponse.getSuccessResponse(result, "Pickup rescheduled successfully");
//    }
//
//    /**
//     * V2: Get secure return by ID
//     *
//     * GET /v2/secure/{id}
//     *
//     * Response: SecureReturnDTO with complete details
//     */
//    @GetMapping("/{id}")
//    public SEResponse<SecureReturnDTO> getSecureReturn(@PathVariable String id) {
//        log.info("V2: Fetching secure return by ID: {}", id);
//
//        SecureReturnDTO result = secureReturnServiceV2.getSecureReturn(id);
//
//        return SEResponse.getSuccessResponse(result, "Secure return retrieved successfully");
//    }
//
//    /**
//     * V2: Get secure return by order ID
//     *
//     * GET /v2/secure/order/{orderId}
//     *
//     * Response: SecureReturnDTO for the given order
//     */
//    @GetMapping("/order/{orderId}")
//    public SEResponse<SecureReturnDTO> getSecureReturnByOrderId(@PathVariable String orderId) {
//        log.info("V2: Fetching secure return by order ID: {}", orderId);
//
//        SecureReturnDTO result = secureReturnServiceV2.getSecureReturnByOrderId(orderId);
//
//        return SEResponse.getSuccessResponse(result, "Secure return retrieved successfully");
//    }
//
//    /**
//     * V2: Get all secure returns for a user
//     *
//     * GET /v2/secure/user/{userId}
//     *
//     * Response: List of SecureReturnDTO for the user
//     */
//    @GetMapping("/user/{userId}")
//    public SEResponse<List<SecureReturnDTO>> getUserSecureReturns(@PathVariable String userId) {
//        log.info("V2: Fetching secure returns for user: {}", userId);
//
//        List<SecureReturnDTO> results = secureReturnServiceV2.getUserSecureReturns(userId);
//
//        return SEResponse.getSuccessResponse(results,
//                String.format("Found %d secure returns for user", results.size()));
//    }
//
//    /**
//     * V2: Get all secure returns for a seller
//     *
//     * GET /v2/secure/seller/{sellerId}
//     *
//     * Response: List of SecureReturnDTO for the seller
//     */
//    @GetMapping("/seller/{sellerId}")
//    public SEResponse<List<SecureReturnDTO>> getSellerSecureReturns(@PathVariable String sellerId) {
//        log.info("V2: Fetching secure returns for seller: {}", sellerId);
//
//        List<SecureReturnDTO> results = secureReturnServiceV2.getSellerSecureReturns(sellerId);
//
//        return SEResponse.getSuccessResponse(results,
//                String.format("Found %d secure returns for seller", results.size()));
//    }
//
//    /**
//     * V2: Get pending appraisals for a seller
//     *
//     * GET /v2/secure/seller/{sellerId}/pending-appraisals
//     *
//     * Response: List of SecureReturnDTO that are delivered to seller and awaiting appraisal
//     */
//    @GetMapping("/seller/{sellerId}/pending-appraisals")
//    public SEResponse<List<SecureReturnDTO>> getSellerPendingAppraisals(@PathVariable String sellerId) {
//        log.info("V2: Fetching pending appraisals for seller: {}", sellerId);
//
//        List<SecureReturnDTO> allReturns = secureReturnServiceV2.getSellerSecureReturns(sellerId);
//
//        // Filter for DELIVERED_TO_SELLER or UNDER_APPRAISAL status
//        List<SecureReturnDTO> pendingAppraisals = allReturns.stream()
//                .filter(sr -> sr.getStatus() == com.sorted.common.enums.SecureReturnStatus.DELIVERED_TO_SELLER
//                        || sr.getStatus() == com.sorted.common.enums.SecureReturnStatus.UNDER_APPRAISAL)
//                .toList();
//
//        return SEResponse.getSuccessResponse(pendingAppraisals,
//                String.format("Found %d pending appraisals", pendingAppraisals.size()));
//    }
//}
