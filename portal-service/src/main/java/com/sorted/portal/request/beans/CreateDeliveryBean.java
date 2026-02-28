package com.sorted.portal.request.beans;

import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreateDeliveryBean extends ReqBaseBean {
    private String order_id;
}
