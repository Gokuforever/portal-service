package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AddressDTO implements Serializable {

    /**
     *
     */
    @Serial
    private static final long serialVersionUID = 1L;
    private String id;
    private String code;
    private String street_1;
    private String street_2;
    private String landmark;
    private String city;
    private String state;
    private String pincode;
    private String address_type;
    private String address_type_desc;
    private Boolean is_default;
    private BigDecimal lat;
    private BigDecimal lng;
    private String phone_no;
    private String first_name;
    private String last_name;

    @JsonIgnore
    public String getFullAddress() {
        if (street_2 == null) {
            return street_1 + ", " + landmark + ", " + city + ", " + state + ", " + pincode;
        }
        return street_1 + ", " + street_2 + ", " + landmark + ", " + city + ", " + state + ", " + pincode;
    }
}
