package com.sorted.portal.response.beans;

import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.enums.WeekDay;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class FindOrderResBean {

    private String id;
    private String code;
    private Long total_amount;
    private String status;
    private List<WeekDay> non_operational_days;
    private List<OrderItemDTO> orderItems;
    private int max_return_days;
    private String creation_date_str;
    private String delivery_partner_id;

}
