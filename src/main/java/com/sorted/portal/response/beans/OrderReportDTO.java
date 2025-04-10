package com.sorted.portal.response.beans;

import com.sorted.commons.entity.mongo.Order_Details;
import lombok.Getter;


@Getter
public class OrderReportDTO {

    private String code;
    private String status;
    private String creation_date;

    public OrderReportDTO(Order_Details orderDetails){
        this.code = orderDetails.getCode();
        this.status = orderDetails.getStatus().getInternal_status();
        this.creation_date = orderDetails.getCreation_date_str();
    }
}
