package com.sorted.portal.request.beans;

import com.sorted.common.beans.AddressDTO;
import com.sorted.common.beans.Bank_Details;
import com.sorted.common.beans.BusinessHours;
import com.sorted.common.beans.Spoc_Details;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class CUDSellerBean extends ReqBaseBean {

    private String name;
    private String seller_id;
    private int status_id;
    private AddressDTO address;
    private String pan_no;
    private String gstin;
    private String primary_mobile_no;
    private String primary_email_id;
    private List<Spoc_Details> spoc_details;
    private Bank_Details bank_details;
    private List<String> serviceable_pincodes;
    private Integer status;
    private boolean resend;
    private BusinessHours business_hours;
}
