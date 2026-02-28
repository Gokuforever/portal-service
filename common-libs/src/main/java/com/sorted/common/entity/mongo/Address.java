package com.sorted.common.entity.mongo;

import com.sorted.common.enums.AddressType;
import com.sorted.common.enums.UserType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "address")
@NoArgsConstructor
public class Address extends BaseMongoEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;
    private String code;
    private String street_1;
    private String street_2;
    private String landmark;
    private String city;
    private String state;
    private String pincode;
    private UserType user_type;
    private String entity_id;
    private AddressType address_type;
    private String address_type_desc;
    private BigDecimal lat;
    private BigDecimal lng;
    private String phone_no;
    private Boolean is_default;
    @Field("first_name")
    private String firstName;
    @Field("last_name")
    private String lastName;

    public String getFullName() {
        if (!StringUtils.hasText(firstName)) return null;
        return firstName + " " + lastName;
    }

    public Address(Address address, UserType userType, String entityId) {
        this.street_1 = address.getStreet_1();
        this.street_2 = address.getStreet_2();
        this.landmark = address.getLandmark();
        this.city = address.getCity();
        this.state = address.getState();
        this.pincode = address.getPincode();
        this.user_type = userType;
        this.entity_id = entityId;
        this.address_type = address.getAddress_type();
        this.address_type_desc = address.getAddress_type_desc();
        this.lat = address.getLat();
        this.lng = address.getLng();
        this.phone_no = address.getPhone_no();
        this.is_default = address.getIs_default();
    }
}
