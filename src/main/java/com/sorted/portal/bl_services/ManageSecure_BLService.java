package com.sorted.portal.bl_services;

import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.InitiateSecureBean;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManageSecure_BLService {

    public void initiateReturn(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {

        InitiateSecureBean secureBean = request.getGenericRequestDataObject(InitiateSecureBean.class);
        CommonUtils.extractHeaders(httpServletRequest, secureBean);

        Preconditions.check(StringUtils.hasText(secureBean.getOrderId()), ResponseCode.MISSING_ORDER_ID);
        Preconditions.check(StringUtils.hasText(secureBean.getReturnDate()), ResponseCode.MISSING_RETURN_DATE);
//        Preconditions.check(CollectionUtils.isNotEmpty());
//        try{
//
//        }


    }

}
