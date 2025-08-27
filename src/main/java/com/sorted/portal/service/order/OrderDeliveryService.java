package com.sorted.portal.service.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.porter.req.beans.CreateOrderBean;
import com.sorted.commons.porter.req.beans.CreateOrderBean.*;
import com.sorted.commons.porter.res.beans.CreateOrderResBean;
import com.sorted.commons.utils.PorterUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;

/**
 * Service for handling order delivery operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderDeliveryService {

    private final PorterUtility porterUtility;

    /**
     * Create a delivery order with Porter
     *
     * @param orderDetails    Order details
     * @param spocDetails     Seller's point of contact details
     * @param deliveryAddress Delivery address
     * @param pickupAddress   Pickup address
     * @param user            Customer details
     * @return CreateOrderResBean containing the Porter order ID and other details
     */
    public CreateOrderResBean createDeliveryOrder(
            Order_Details orderDetails,
            Spoc_Details spocDetails,
            AddressDTO deliveryAddress,
            AddressDTO pickupAddress,
            Users user) throws JsonProcessingException {

        log.debug("Creating Porter delivery order for order ID: {}", orderDetails.getId());

        CreateOrderBean orderRequest = buildPorterOrderRequest(
                orderDetails,
                spocDetails,
                deliveryAddress,
                pickupAddress,
                user);

        log.debug("Sending order creation request to Porter");
        CreateOrderResBean response = porterUtility.createOrder(orderRequest);

        log.info("Successfully created Porter delivery order with ID: {} for order: {}",
                response.getOrder_id(), orderDetails.getId());

        return response;
    }

    /**
     * Build the Porter order request object
     *
     * @param orderDetails    Order details
     * @param spocDetails     SPOC details
     * @param deliveryAddress Delivery address
     * @param pickupAddress   Pickup address
     * @param user            User details
     * @return CreateOrderBean for Porter API
     */
    private CreateOrderBean buildPorterOrderRequest(
            Order_Details orderDetails,
            Spoc_Details spocDetails,
            AddressDTO deliveryAddress,
            AddressDTO pickupAddress,
            Users user) {

        // Create delivery instructions
        Delivery_Instructions instruction = Delivery_Instructions.builder()
                .type("text")
                .description("handle with care")
                .build();

        Instruction_List instructionsList = Instruction_List.builder()
                .instructions_list(Collections.singletonList(instruction))
                .build();

        // Create pickup details
        Address pickupAddressObj = buildAddress(
                pickupAddress,
                spocDetails.getFirst_name() + " " + spocDetails.getLast_name(),
                spocDetails.getMobile_no());

        Pickup_Details pickupDetails = Pickup_Details.builder()
                .address(pickupAddressObj)
                .build();

        // Create drop details
        Address dropAddressObj = buildAddress(
                deliveryAddress,
                user.getFirst_name() + " " + user.getLast_name(),
                StringUtils.hasText(deliveryAddress.getPhone_no()) ? deliveryAddress.getPhone_no() : user.getMobile_no());

        Drop_Details dropDetails = Drop_Details.builder()
                .address(dropAddressObj)
                .build();

        // Create the main order bean
        return CreateOrderBean.builder()
                .request_id(orderDetails.getCode())
                .delivery_instructions(instructionsList)
                .pickup_details(pickupDetails)
                .drop_details(dropDetails)
                .build();
    }

    /**
     * Build Address object for Porter API
     *
     * @param addressDTO   Address DTO from our system
     * @param contactName  Contact name
     * @param contactPhone Contact phone number
     * @return Address object for Porter API
     */
    private Address buildAddress(AddressDTO addressDTO, String contactName, String contactPhone) {
        return Address.builder()
                .street_address1(addressDTO.getStreet_1())
                .street_address2(addressDTO.getStreet_2())
                .landmark(addressDTO.getLandmark())
                .city(addressDTO.getCity())
                .state(addressDTO.getState())
                .pincode(addressDTO.getPincode())
                .country("India")
                .lat(addressDTO.getLat())
                .lng(addressDTO.getLng())
                .contact_details(Contact_Details.builder()
                        .name(contactName)
                        .phone_number("+91" + contactPhone)
                        .build())
                .build();
    }
} 