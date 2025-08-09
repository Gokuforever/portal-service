package com.sorted.portal.bl_services;

import com.sorted.commons.beans.*;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.utils.*;
import com.sorted.portal.request.beans.GenerateInvoiceBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@RestController
@RequiredArgsConstructor
public class ManageInvoice_BLService {

    private final Users_Service usersService;
    private final Order_Details_Service orderService;
    private final GenerateInvoiceService generateInvoiceService;

    @PostMapping("/generateInvoice")
    public String generateInvoice(@RequestBody SERequest request, HttpServletRequest httpServletRequest) throws IOException {
        GenerateInvoiceBean req = request.getGenericRequestDataObject(GenerateInvoiceBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);

        UsersBean usersBean = usersService.validateUserForActivity(req, Permission.VIEW, Activity.ORDER_MANAGEMENT);
        Preconditions.check(usersBean.getRole().getUser_type() == UserType.CUSTOMER, ResponseCode.ACCESS_DENIED);
        Preconditions.check(StringUtils.hasText(req.getOrderId()), ResponseCode.MANDATE_ORDER_ID);

        Order_Details orderDetails = orderService.findById(req.getOrderId()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ORDER_NOT_FOUND));
        return generateInvoiceService.generateInvoice(orderDetails);
    }

    public static void main(String[] args) {
        // For amount in words
        String amountInWords = IndianCurrencyConverter.convertToWords(12000.10);
        System.out.println(amountInWords);
    }
}
