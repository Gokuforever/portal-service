package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Request bean for appraising a secure return
 * Rating is MANDATORY - system calculates refund based on rating
 * Amount is OPTIONAL - only used to override the calculated refund
 * Rating system: 1-5 (5 = 50%, 4 = 40%, 3 = 30%, 2 = 20%, 1 = 10% of selling_price_after_discount)
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AppraiseSecureReturn extends ReqBaseBean {
    
    @JsonProperty("order_id")
    private String orderId;
    
    /**
     * Rating from 1 to 5 for the returned product condition
     * 5 = Excellent (50% refund), 4 = Good (40%), 3 = Fair (30%), 2 = Poor (20%), 1 = Very Poor (10%)
     * MANDATORY - Must be provided for all appraisals
     */
    private Integer rating;
    
    /**
     * Refund amount to be given to customer (in rupees)
     * OPTIONAL - If provided, overrides the calculated refund amount based on rating
     * If not provided, amount is auto-calculated from rating
     * Note: Will be converted to paise internally for storage
     */
    private BigDecimal amount;
    
    /**
     * Seller's remarks about the returned product condition
     * Optional
     */
    private String remark;
}
