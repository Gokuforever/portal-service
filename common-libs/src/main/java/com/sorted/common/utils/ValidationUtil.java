package com.sorted.common.utils;

import com.sorted.common.beans.AddressDTO;
import com.sorted.common.beans.Bank_Details;
import com.sorted.common.entity.mongo.Address;
import com.sorted.common.enums.AddressType;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

public class ValidationUtil {

    public static Address validateAddress(AddressDTO address, Address address2) {
        String street_1 = address.getStreet_1();
        String street_2 = address.getStreet_2();
        String landmark = address.getLandmark();
        String city = address.getCity();
        String state = address.getState();
        String pincode = address.getPincode();
        String phoneNo = address.getPhone_no();
        BigDecimal lat = address.getLat();
        BigDecimal lng = address.getLng();
        String address_type_desc = address.getAddress_type_desc();
        String firstName = address.getFirst_name();
        String lastName = address.getLast_name();

        Preconditions.check(StringUtils.hasText(firstName), ResponseCode.MANDATE_FIRST_NAME);
        Preconditions.check(StringUtils.hasText(lastName), ResponseCode.MANDATE_FIRST_NAME);
        Preconditions.check(StringUtils.hasText(street_1), ResponseCode.MANDATE_STREET);
        Preconditions.check(StringUtils.hasText(landmark), ResponseCode.MANDATE_LANDMARK);
        Preconditions.check(StringUtils.hasText(city), ResponseCode.MANDATE_CITY);
        Preconditions.check(StringUtils.hasText(state), ResponseCode.MANDATE_STATE);
        Preconditions.check(StringUtils.hasText(pincode), ResponseCode.MANDATE_PINCODE);
        Preconditions.check(SERegExpUtils.isPincode(pincode), ResponseCode.INVALID_PINCODE);

        street_1 = trimAndValidateLength(street_1, ResponseCode.INVALID_STREET);
        if (StringUtils.hasText(street_2)) {
            street_2 = trimAndValidateLength(street_2, ResponseCode.INVALID_STREET_2);
        }
        landmark = trimAndValidateLength(landmark, ResponseCode.INVALID_LANDMARK);
        city = trimAndValidateLength(city, ResponseCode.INVALID_CITY);
        state = trimAndValidateLength(state, ResponseCode.INVALID_STATE);
        AddressType address_type = AddressType.getByName(address.getAddress_type());
        if (address_type == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ADDRESS_TYPE);
        }
        if (address_type != AddressType.STORE && !StringUtils.hasText(phoneNo)) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_PHONE);
        }
        if (address_type != AddressType.STORE && !SERegExpUtils.isMobileNo(phoneNo)) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PHONE);
        }
        if (!StringUtils.hasText(address.getAddress_type())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ADDRESS_TYPE);
        }
        if (address_type == AddressType.OTHER) {
            if (!StringUtils.hasText(address_type_desc)) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_OTHER_ADDRESS_DESC);
            }
            if (!SERegExpUtils.standardTextValidation(address_type_desc)) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_OTHER_ADDRESS_DESC);
            }
        } else {
            address_type_desc = null;
        }

        address2.setFirstName(firstName.trim());
        address2.setLastName(lastName.trim());
        address2.setStreet_1(street_1);
        address2.setStreet_2(street_2);
        address2.setLandmark(landmark);
        address2.setCity(city);
        address2.setState(state);
        address2.setPincode(pincode);
        address2.setAddress_type(address_type);
        address2.setAddress_type_desc(address_type_desc);
        address2.setLat(lat);
        address2.setLng(lng);
        if (StringUtils.hasText(phoneNo)) {
            address2.setPhone_no(phoneNo);
        }
        return address2;
    }

    @NotNull
    private static String trimAndValidateLength(String city, ResponseCode responseCode) {
        city = city.trim().replaceAll("\\s+", " ");
        if (city.length() > 100) {
            throw new CustomIllegalArgumentsException(responseCode);
        }
        return city;
    }

    public static void validateBankDetails(Bank_Details bank_details) {
        String account_number = bank_details.getAccount_number();
        String ifsc_code = bank_details.getIfsc_code();
        String branch_name = bank_details.getBranch_name();
        String bank_name = bank_details.getBank_name();

        Preconditions.check(StringUtils.hasText(account_number), ResponseCode.MANDATE_ACC_NO);
        Preconditions.check(StringUtils.hasText(ifsc_code), ResponseCode.MANDATE_IFSC);
        Preconditions.check(StringUtils.hasText(branch_name), ResponseCode.MANDATE_BRANCH_NAME);
        Preconditions.check(StringUtils.hasText(bank_name), ResponseCode.MANDATE_BANK_NAME);
    }

}
