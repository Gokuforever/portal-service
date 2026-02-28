package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sorted.common.enums.PurchaseType;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class FindOrderReqBean extends ReqBaseBean {

    private String code;
    private String order_status;
    private String from_date;
    private String to_date;
    @JsonIgnore
    private PurchaseType purchase_type;
}
