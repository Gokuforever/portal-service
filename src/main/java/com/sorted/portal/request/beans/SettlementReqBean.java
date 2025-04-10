package com.sorted.portal.request.beans;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.beans.SettlementDetails;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class SettlementReqBean extends ReqBaseBean {
    @JsonProperty("order_id")
    private String orderId;
    @JsonProperty("settlement_details")
    private SettlementDetails settlementDetails;

}
