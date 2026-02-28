package com.sorted.common.entity.mongo;

import com.sorted.common.beans.SettlementDetails;
import com.sorted.common.enums.SettlementStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "weekly_settlement_reports")
public class Weekly_Settlement_Report extends BaseMongoEntity<String> {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    // Seller information
    private String seller_id;
    private String seller_name;
    private String seller_email;
    
    // Week identification
    private String week_id;              // "2026-W09"
    private LocalDate week_start;        // Monday
    private LocalDate week_end;          // Sunday
    private Integer year;
    private Integer week_number;
    
    // Financial summary (all amounts in paise)
    private Long total_orders_amount;    // Total order amount
    private Long studeaze_share_total;   // Platform fees (10%)
    private Long seller_share_total;     // Seller's 90%
    private Long secure_return_deductions; // Refunds deducted
    private Long net_payout_amount;      // Final amount to pay
    
    // Order tracking
    private Integer total_orders_count;
    private Integer delivered_orders_count;
    private Integer secure_return_orders_count;
    private List<String> order_ids;      // All order IDs in this settlement
    private List<String> secure_return_order_ids;
    
    // Settlement status
    private SettlementStatus status;     // PENDING, EMAIL_SENT, PAID, DISPUTED
    private LocalDateTime email_sent_date;
    private LocalDateTime settlement_date;
    private SettlementDetails settlement_details;
    
    // Dispute tracking (future)
    private Boolean has_dispute;
    private String dispute_reason;
    private LocalDateTime dispute_raised_date;
    private String dispute_resolution;
    
    // Audit
    private String settled_by;           // Admin user ID who settled
    private String remarks;
}
