package com.sorted.portal.response.beans;

import lombok.Data;

@Data
public class PGResponseBean {

    private String razorpay_payment_id;
    private String razorpay_order_id;
    private String razorpay_signature;
    private String order_id;
}
