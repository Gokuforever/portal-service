package com.sorted.portal.response.beans;

import com.sorted.common.beans.AddressDTO;
import com.sorted.common.enums.RefundStatus;
import com.sorted.common.enums.SecureReturnStatus;
import com.sorted.common.enums.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Secure Return response.
 * Used in API responses to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureReturnDTO {

    private String id;
    private String order_id;
    private String order_code;
    private String secure_order_code;
    private String user_id;
    private String seller_id;

    // Status
    private SecureReturnStatus status;
    private String status_description;

    // Scheduling
    private LocalDate scheduled_pickup_date;
    private TimeSlot scheduled_time_slot;
    private LocalDateTime actual_pickup_time;
    private Integer reschedule_count;
    private Integer max_reschedule_allowed;
    private Boolean can_reschedule;

    // Addresses
    private AddressDTO pickup_address;
    private AddressDTO delivery_address;

    // Delivery Partner
    private String dp_order_id;
    private String dp_tracking_url;
    private Long estimated_delivery_charges;
    private Long actual_delivery_charges;

    // Items
    private List<SecureReturnItemDTO> items;
    private Integer total_items_count;

    // Financial
    private Long total_estimated_refund;
    private Long total_actual_refund;
    private String refund_transaction_id;
    private RefundStatus refund_status;
    private String refund_status_description;
    private LocalDateTime refund_initiated_at;
    private LocalDateTime refund_completed_at;

    // Failure
    private String failure_reason;
    private String rejection_remarks;

    // Audit
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
