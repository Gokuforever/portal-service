package com.sorted.portal.response.beans;

import com.sorted.common.enums.SecureReturnStatus;
import com.sorted.common.enums.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response bean for secure pickup details.
 * Used when user clicks the confirmation link from email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurePickupDetailsBean {

    private String secureReturnId;
    private String orderCode;
    private String secureOrderCode;
    
    // Pickup Schedule
    private LocalDate scheduledPickupDate;
    private TimeSlot timeSlot;
    private String timeSlotDisplay;
    
    // Status
    private SecureReturnStatus status;
    private String statusDescription;
    private boolean canConfirm;
    
    // Customer Info
    private String customerName;
    
    // Pickup Address
    private String pickupAddress;
    
    // Items
    private List<PickupItemDetail> items;
    private int totalItems;
    
    // Refund Info
    private BigDecimal estimatedRefund;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PickupItemDetail {
        private String productName;
        private String productImageUrl;
        private int quantity;
        private BigDecimal sellingPrice;
        private BigDecimal estimatedRefund;
    }
}
