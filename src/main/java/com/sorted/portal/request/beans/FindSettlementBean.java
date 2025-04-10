package com.sorted.portal.request.beans;


import com.sorted.commons.helper.ReqBaseBean;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindSettlementBean extends ReqBaseBean {

    private String order_id;
    private Boolean status;
}
