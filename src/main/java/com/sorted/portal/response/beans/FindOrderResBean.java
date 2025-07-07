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
    private String transaction_id;
    private List<WeekDay> non_operational_days;
    private AddressDTO delivery_address;
    private AddressDTO pickup_address;
    private List<OrderItemDTO> orderItems;
    private int max_return_days;
    private String creation_date_str;

}
