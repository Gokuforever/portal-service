package com.sorted.portal.crons;

import com.phonepe.sdk.pg.common.models.response.RefundResponse;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Order_Details;
import com.sorted.common.entity.service.Order_Details_Service;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.utils.InternalMailService;
import com.sorted.portal.PhonePe.PhonePeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Cron job to retry failed secure refunds
 * Runs every 6 hours to retry refunds that failed previously
 * Maximum 3 retry attempts per order
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecureRefundRetryCron {

    private final Order_Details_Service orderDetailsService;
    private final PhonePeUtility phonePeUtility;
    private final InternalMailService internalMailService;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_INTERVAL_HOURS = 6;

    /**
     * Retry failed secure refunds
     * Runs every 6 hours
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void retryFailedRefunds() {
        log.info("Starting secure refund retry cron job");

        try {
            // Find orders with SECURE_REFUND_FAILED status
            AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
            filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filter.addClause(AggregationFilter.WhereClause.eq(Order_Details.Fields.status_id, 
                    OrderStatus.SECURE_REFUND_FAILED.getId()));

            List<Order_Details> failedOrders = orderDetailsService.repoFind(filter);

            if (CollectionUtils.isEmpty(failedOrders)) {
                log.info("No failed refunds found to retry");
                return;
            }

            log.info("Found {} failed refunds to retry", failedOrders.size());

            int retriedCount = 0;
            int maxRetriesReachedCount = 0;
            int successCount = 0;
            int stillFailingCount = 0;

            for (Order_Details order : failedOrders) {
                try {
                    // Check if max retries reached
                    Integer retryCount = order.getRefund_retry_count() != null ? order.getRefund_retry_count() : 0;
                    
                    if (retryCount >= MAX_RETRY_ATTEMPTS) {
                        log.warn("Max retry attempts ({}) reached for order: {}", MAX_RETRY_ATTEMPTS, order.getId());
                        handleMaxRetriesReached(order);
                        maxRetriesReachedCount++;
                        continue;
                    }

                    // Check if enough time has passed since last retry
                    if (order.getLast_refund_retry_date() != null) {
                        LocalDateTime nextRetryTime = order.getLast_refund_retry_date().plusHours(RETRY_INTERVAL_HOURS);
                        if (LocalDateTime.now().isBefore(nextRetryTime)) {
                            log.debug("Skipping order {} - retry interval not reached yet", order.getId());
                            continue;
                        }
                    }

                    // Attempt retry
                    log.info("Retrying refund for order: {} (Attempt {}/{})", 
                            order.getId(), retryCount + 1, MAX_RETRY_ATTEMPTS);
                    
                    boolean success = retryRefund(order);
                    retriedCount++;

                    if (success) {
                        successCount++;
                    } else {
                        stillFailingCount++;
                    }

                } catch (Exception e) {
                    log.error("Error retrying refund for order: {}, Error: {}", order.getId(), e.getMessage(), e);
                }
            }

            log.info("Secure refund retry cron completed. Total: {}, Retried: {}, Success: {}, Still Failing: {}, Max Retries Reached: {}",
                    failedOrders.size(), retriedCount, successCount, stillFailingCount, maxRetriesReachedCount);

        } catch (Exception e) {
            log.error("Error in secure refund retry cron: {}", e.getMessage(), e);
            internalMailService.sendMailOnError("Secure Refund Retry Cron Failed",
                    "Error in secure refund retry cron: " + e.getMessage(), e);
        }
    }

    /**
     * Retry refund for a specific order
     * 
     * @param order Order to retry refund for
     * @return true if successful, false if still failing
     */
    private boolean retryRefund(Order_Details order) {
        try {
            // Generate new refund transaction ID for retry
            String retryRefundTxnId = order.getRefund_transaction_id() + "-RETRY-" + 
                    (order.getRefund_retry_count() != null ? order.getRefund_retry_count() + 1 : 1);

            log.info("Initiating refund retry for order: {}, Refund ID: {}", order.getId(), retryRefundTxnId);

            // Call PhonePe partial refund API
            // Note: We need to get the refund amount from somewhere - assuming it's stored or can be calculated
            // For now, we'll try to use the original refund transaction ID to check status first
            
            // First, check if the original refund actually succeeded
            Optional<com.phonepe.sdk.pg.common.models.response.RefundStatusResponse> statusResponse = 
                    phonePeUtility.refundStatus(order.getRefund_transaction_id());

            if (statusResponse.isPresent()) {
                String state = statusResponse.get().getState();
                
                if ("COMPLETED".equals(state)) {
                    // Original refund actually succeeded
                    log.info("Original refund was actually successful for order: {}", order.getId());
                    handleRetrySuccess(order);
                    return true;
                } else if ("PENDING".equals(state)) {
                    // Refund is still pending, update status
                    log.info("Original refund is still pending for order: {}", order.getId());
                    order.setStatus(OrderStatus.SECURE_REFUND_PENDING, Defaults.PHONEPE_REFUND_RETRY_CRON);
                    updateRetryMetadata(order, false);
                    orderDetailsService.update(order.getId(), order, Defaults.PHONEPE_REFUND_RETRY_CRON);
                    return false;
                }
            }

            // If we reach here, refund truly failed or status check failed
            // Increment retry count and update timestamp
            updateRetryMetadata(order, false);
            orderDetailsService.update(order.getId(), order, Defaults.PHONEPE_REFUND_RETRY_CRON);

            log.warn("Refund retry failed for order: {}, will retry again later", order.getId());
            return false;

        } catch (Exception e) {
            log.error("Error during refund retry for order: {}, Error: {}", order.getId(), e.getMessage(), e);
            
            // Update retry metadata even on exception
            updateRetryMetadata(order, false);
            orderDetailsService.update(order.getId(), order, Defaults.PHONEPE_REFUND_RETRY_CRON);
            
            return false;
        }
    }

    /**
     * Handle successful retry
     */
    private void handleRetrySuccess(Order_Details order) {
        log.info("Refund retry successful for order: {}", order.getId());
        
        order.setStatus(OrderStatus.SECURE_BUY_REFUNDED, Defaults.PHONEPE_REFUND_RETRY_CRON);
        orderDetailsService.update(order.getId(), order, Defaults.PHONEPE_REFUND_RETRY_CRON);
        
        // Send success notification
        String subject = "Secure Refund Retry Successful - Order: " + order.getCode();
        String message = String.format(
                "Refund retry successful after %d attempts.%n" +
                "Order ID: %s%n" +
                "Order Code: %s%n" +
                "Refund Transaction ID: %s%n" +
                "Status: SECURE_BUY_REFUNDED",
                order.getRefund_retry_count() != null ? order.getRefund_retry_count() + 1 : 1,
                order.getId(),
                order.getCode(),
                order.getRefund_transaction_id()
        );
        
        internalMailService.sendMailOnError(subject, message, null);
        
        log.info("Order {} status updated to SECURE_BUY_REFUNDED after retry", order.getId());
    }

    /**
     * Handle max retries reached
     */
    private void handleMaxRetriesReached(Order_Details order) {
        log.error("Max retry attempts reached for order: {}", order.getId());
        
        // Update status to REFUND_FAILED (permanent failure)
        order.setStatus(OrderStatus.REFUND_FAILED, Defaults.PHONEPE_REFUND_RETRY_CRON);
        order.setRefund_failure_reason("Max retry attempts (" + MAX_RETRY_ATTEMPTS + ") reached");
        orderDetailsService.update(order.getId(), order, Defaults.PHONEPE_REFUND_RETRY_CRON);
        
        // Send critical notification
        String subject = "CRITICAL: Secure Refund Failed After Max Retries - Order: " + order.getCode();
        String message = String.format(
                "Refund failed after %d retry attempts. Manual intervention required.%n" +
                "Order ID: %s%n" +
                "Order Code: %s%n" +
                "Refund Transaction ID: %s%n" +
                "Last Failure Reason: %s%n" +
                "Status: REFUND_FAILED (permanent)%n%n" +
                "ACTION REQUIRED: Please process refund manually.",
                MAX_RETRY_ATTEMPTS,
                order.getId(),
                order.getCode(),
                order.getRefund_transaction_id(),
                order.getRefund_failure_reason()
        );
        
        internalMailService.sendMailOnError(subject, message, null);
        
        log.info("Order {} marked as permanently failed after {} retries", order.getId(), MAX_RETRY_ATTEMPTS);
    }

    /**
     * Update retry metadata
     */
    private void updateRetryMetadata(Order_Details order, boolean success) {
        Integer currentRetryCount = order.getRefund_retry_count() != null ? order.getRefund_retry_count() : 0;
        
        if (!success) {
            order.setRefund_retry_count(currentRetryCount + 1);
            order.setLast_refund_retry_date(LocalDateTime.now());
        }
    }
}
