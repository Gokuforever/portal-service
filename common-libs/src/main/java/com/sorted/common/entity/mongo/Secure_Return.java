package com.sorted.common.entity.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sorted.common.beans.AddressDTO;
import com.sorted.common.beans.DeliveryRequestAttempts;
import com.sorted.common.beans.Secure_Return_Item;
import com.sorted.common.beans.Secure_Status_History;
import com.sorted.common.enums.RefundStatus;
import com.sorted.common.enums.SecureReturnStatus;
import com.sorted.common.enums.TimeSlot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Secure Return entity for managing secure buy/return process.
 * Handles the complete lifecycle from scheduling to refund completion.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "secure_returns")
public class Secure_Return extends BaseMongoEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    // References
    private String order_id;              // Link to Order_Details
    private String order_code;            // For display
    private String user_id;               // Customer
    private String seller_id;             // Seller
    private String secure_order_code;     // e.g., SEC-ORD-MAR2026-123456

    // Status Management
    private SecureReturnStatus status;
    private Integer status_id;
    private List<Secure_Status_History> status_history;

    // Scheduling
    private LocalDate scheduled_pickup_date;
    private TimeSlot scheduled_time_slot;
    private LocalDateTime actual_pickup_time;
    private Integer reschedule_count;
    private Integer max_reschedule_allowed;  // Default: 2

    // Addresses
    private AddressDTO pickup_address;       // Customer address
    private AddressDTO delivery_address;     // Seller address

    // Delivery Partner
    private String dp_order_id;              // Porter order ID
    private String dp_tracking_url;
    private Long estimated_delivery_charges;
    private Long actual_delivery_charges;
    private List<DeliveryRequestAttempts> pickup_request_attempts;

    // Items
    private List<Secure_Return_Item> items;

    // Financial Summary
    private Long total_estimated_refund;     // Sum of all items (optimistic)
    private Long total_actual_refund;        // After appraisal
    private String merchant_refund_id;
    private String refund_transaction_id;    // PhonePe/Razorpay transaction ID
    private RefundStatus refund_status;
    private LocalDateTime refund_initiated_at;
    private LocalDateTime refund_completed_at;

    // Pickup Confirmation
    private Boolean confirmation_email_sent;
    private LocalDateTime confirmation_email_sent_at;
    private String confirmation_token;

    // Failure Handling
    private String failure_reason;
    private String rejection_remarks;

    // Audit
    @Version
    private Long version;

    /**
     * Set status with history tracking
     */
    @JsonIgnore
    public void setStatus(@NonNull SecureReturnStatus status, String changedBy, String remarks) {
        Secure_Status_History history = Secure_Status_History.builder()
                .status(status)
                .changed_at(LocalDateTime.now())
                .changed_by(changedBy)
                .remarks(remarks)
                .build();

        List<Secure_Status_History> historyList = CollectionUtils.isEmpty(getStatus_history())
                ? new ArrayList<>()
                : getStatus_history();
        historyList.add(history);

        setStatus_history(historyList);
        this.status = status;
        this.status_id = status.getId();
    }

    /**
     * Set status without remarks
     */
    @JsonIgnore
    public void setStatus(@NonNull SecureReturnStatus status, String changedBy) {
        setStatus(status, changedBy, null);
    }

    /**
     * Calculate total estimated refund from all items
     */
    @JsonIgnore
    public void calculateTotalEstimatedRefund() {
        if (CollectionUtils.isEmpty(items)) {
            this.total_estimated_refund = 0L;
            return;
        }
        this.total_estimated_refund = items.stream()
                .mapToLong(item -> item.getEstimated_refund_amount() != null ? item.getEstimated_refund_amount() : 0L)
                .sum();
    }

    /**
     * Calculate total actual refund from all items (after appraisal)
     */
    @JsonIgnore
    public void calculateTotalActualRefund() {
        if (CollectionUtils.isEmpty(items)) {
            this.total_actual_refund = 0L;
            return;
        }
        this.total_actual_refund = items.stream()
                .mapToLong(item -> item.getActual_refund_amount() != null ? item.getActual_refund_amount() : 0L)
                .sum();
    }

    /**
     * Check if all items have been appraised
     */
    @JsonIgnore
    public boolean areAllItemsAppraised() {
        if (CollectionUtils.isEmpty(items)) {
            return false;
        }
        return items.stream()
                .allMatch(item -> item.getItem_status() != null && item.getItem_status().isAppraised());
    }

    /**
     * Increment reschedule count
     */
    @JsonIgnore
    public void incrementRescheduleCount() {
        this.reschedule_count = (this.reschedule_count != null ? this.reschedule_count : 0) + 1;
    }

    /**
     * Check if reschedule is allowed
     */
    @JsonIgnore
    public boolean canReschedule() {
        if (this.status == null || !this.status.canReschedule()) {
            return false;
        }
        int currentCount = this.reschedule_count != null ? this.reschedule_count : 0;
        int maxAllowed = this.max_reschedule_allowed != null ? this.max_reschedule_allowed : 2;
        return currentCount < maxAllowed;
    }

    // Private setters to prevent direct status modification
    private void setStatus(SecureReturnStatus status) {}
    private void setStatus_id(Integer status_id) {}
}
