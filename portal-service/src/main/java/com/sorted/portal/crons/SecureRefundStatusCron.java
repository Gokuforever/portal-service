package com.sorted.portal.crons;

import com.phonepe.sdk.pg.common.models.response.RefundStatusResponse;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Order_Details;
import com.sorted.common.entity.service.Order_Details_Service;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.utils.InternalMailService;
import com.sorted.portal.PhonePe.PhonePeUtility;;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Cron job to check PhonePe refund status for secure returns
 * Runs every 2 hours to check orders in SECURE_REFUND_PENDING status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecureRefundStatusCron {

    private final Order_Details_Service orderDetailsService;
    private final PhonePeUtility phonePeUtility;
    private final InternalMailService internalMailService;

    /**
     * Check PhonePe refund status for secure return orders
     * Runs every 2 hours
     */
    @Scheduled(cron = "0 0 */2 * * *") // Every 2 hours
    public void checkSecureRefundStatus() {
        log.info("Starting secure refund status check cron job");

        // Find all orders with SECURE_REFUND_PENDING status
        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(AggregationFilter.WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.SECURE_REFUND_PENDING.getId()));

        List<Order_Details> orders = orderDetailsService.repoFind(filter);

        if (CollectionUtils.isEmpty(orders)) {
            log.info("No orders found with SECURE_REFUND_PENDING status");
            return;
        }

        log.info("Found {} orders with SECURE_REFUND_PENDING status", orders.size());

        int successCount = 0;
        int failedCount = 0;
        int pendingCount = 0;

        for (Order_Details order : orders) {
            try {
                processRefundStatus(order);
                
                // Check updated status
                if (order.getStatus() == OrderStatus.PARTIALLY_REFUNDED) {
                    successCount++;
                } else if (order.getStatus() == OrderStatus.REFUND_FAILED) {
                    failedCount++;
                } else {
                    pendingCount++;
                }
            } catch (Exception e) {
                log.error("Error processing refund status for order: {}", order.getId(), e);
                pendingCount++;
            }
        }

        log.info("Secure refund status check completed. Success: {}, Failed: {}, Still Pending: {}", 
                successCount, failedCount, pendingCount);
    }

    /**
     * Process refund status for a single order
     */
    private void processRefundStatus(Order_Details order) {
        String refundTxnId = order.getRefund_transaction_id();
        
        if (refundTxnId == null || refundTxnId.isEmpty()) {
            log.warn("Order {} has SECURE_REFUND_PENDING status but no refund_transaction_id", order.getId());
            return;
        }

        log.debug("Checking refund status for order: {}, refundTxnId: {}", order.getId(), refundTxnId);

        // Check refund status with PhonePe
        Optional<RefundStatusResponse> refundStatusResponse = phonePeUtility.refundStatus(refundTxnId);
        
        if (refundStatusResponse.isEmpty()) {
            log.warn("Empty response from PhonePe refund status check for order: {}", order.getId());
            return;
        }

        RefundStatusResponse response = refundStatusResponse.get();
        String state = response.getState();
        
        log.info("PhonePe refund status for order {}: {}", order.getId(), state);

        // Update order status based on PhonePe response
        switch (state) {
            case "COMPLETED":
                handleRefundSuccess(order);
                break;
                
            case "FAILED":
                handleRefundFailure(order);
                break;
                
            default:
                log.info("Refund still pending for order: {}. State: {}", order.getId(), state);
                // Keep status as SECURE_REFUND_PENDING
                break;
        }
    }

    /**
     * Handle successful refund
     */
    private void handleRefundSuccess(Order_Details order) {
        log.info("Refund completed successfully for order: {}", order.getId());
        
        order.setStatus(OrderStatus.PARTIALLY_REFUNDED, Defaults.PHONEPE_REFUND_CRON);
        orderDetailsService.update(order.getId(), order, Defaults.PHONEPE_REFUND_CRON);
        
        log.info("Order {} status updated to PARTIALLY_REFUNDED", order.getId());
    }

    /**
     * Handle failed refund
     */
    private void handleRefundFailure(Order_Details order) {
        log.error("Refund failed for order: {}", order.getId());
        
        order.setStatus(OrderStatus.REFUND_FAILED, Defaults.PHONEPE_REFUND_CRON);
        orderDetailsService.update(order.getId(), order, Defaults.PHONEPE_REFUND_CRON);
        
        // Send error notification email
        String subject = "Secure Return Refund Failed - Order: " + order.getCode();
        String message = String.format(
                "PhonePe refund failed for secure return.%n" +
                "Order ID: %s%n" +
                "Order Code: %s%n" +
                "Refund Transaction ID: %s%n" +
                "Please investigate and process refund manually.",
                order.getId(), 
                order.getCode(), 
                order.getRefund_transaction_id()
        );
        
        internalMailService.sendMailOnError(subject, message, null);
        
        log.info("Order {} status updated to REFUND_FAILED and error email sent", order.getId());
    }
}
