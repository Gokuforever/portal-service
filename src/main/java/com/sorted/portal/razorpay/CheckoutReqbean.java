package com.sorted.portal.razorpay;

import lombok.Data;

@Data
public class CheckoutReqbean {

    private String key;
    private int amount;
    private String currency;
    private String name;
    private String description;
    private String image;
    private String order_id;
    private CustomerDetails prefill;
    private Notes notes;
    private Theme theme;

    @Data
    public static class CustomerDetails {
        private String name;
        private String email;
        private String contact;

    }

    @Data
    public static class Notes {
        private String address;
    }

    @Data
    public static class Theme {
        private String color;
    }

}
