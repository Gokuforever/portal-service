package com.sorted.portal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.entity.mongo.Address;
import com.sorted.commons.entity.service.Address_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.porter.req.beans.GetQuoteRequest;
import com.sorted.commons.porter.res.beans.GetQuoteResponse;
import com.sorted.commons.utils.PorterUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EstimateDeliveryService {

    private final PorterUtility porterUtility;
    private final Address_Service addressService;

    public GetQuoteResponse getEstimateDeliveryAmount(String pickup_address_id, String delivery_address_id, String customerName) throws JsonProcessingException {
        Optional<Address> deliveryAddress = addressService.findById(pickup_address_id);
        if (deliveryAddress.isEmpty()) {
            throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
        }
        Optional<Address> pickUpAddress = addressService.findById(delivery_address_id);
        if (pickUpAddress.isEmpty()) {
            throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
        }
        Address address = deliveryAddress.get();
        String mobile = StringUtils.hasText(address.getPhone_no()) ? address.getPhone_no() : "9867292392";
        GetQuoteRequest getQuoteRequest = porterUtility.buildGetQuoteRequest(pickUpAddress.get(), address, mobile, customerName);
        return porterUtility.getDeliveryQuote(getQuoteRequest);
    }
}
