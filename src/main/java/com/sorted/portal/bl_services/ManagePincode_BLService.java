package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.sorted.commons.beans.NearestSellerRes;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.Semester;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.GsonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.NearestSellerReq;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ManagePincode_BLService {

    private final PorterUtility porterUtility;
    private final Users_Service users_Service;


    @PostMapping("/delivery/check")
    public SEResponse checkDelivery(@RequestBody SERequest request, HttpServletRequest httpServletRequest)
            throws JsonProcessingException {

        NearestSellerReq req = request.getGenericRequestDataObject(NearestSellerReq.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.HOME);
        Role role = usersBean.getRole();
        UserType user_type = role.getUser_type();
        switch (user_type) {
            case CUSTOMER, GUEST:
                break;
            default:
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
        if (!StringUtils.hasText(req.getPincode())) {
//				&& (req.getLat() == null || req.getLat().compareTo(BigDecimal.ZERO) < 1 || req.getLng() == null
//						|| req.getLng().compareTo(BigDecimal.ZERO) < 1)) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_PINCODE);
        }

        if (!StringUtils.hasText(req.getBranch())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_BRANCH_NAME);
        }
        boolean isOtherBranch = req.getBranch().equalsIgnoreCase("other");
        if (isOtherBranch && !StringUtils.hasText(req.getDesc())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SEMESTER_DESC);
        }
        if (!StringUtils.hasText(req.getSemester())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SEMISTER);
        }
        if (!StringUtils.hasText(req.getCollege())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_COLLEGE_NAME);
        }
        String pincode = req.getPincode();
        if (!SERegExpUtils.isPincode(pincode)) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PINCODE);
        }
        if (!SERegExpUtils.standardTextValidation(req.getBranch())) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_BRANCH);
        }
        Semester semester = Semester.getByAlias(req.getSemester());
        if (semester == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SEMISTER);
        }
        if (!SERegExpUtils.standardTextValidation(req.getCollege())) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_COLLEGE_NAME);
        }
        NearestSellerRes response = porterUtility.getNearestSeller(pincode, usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getId());
        Gson gson = GsonUtils.getGson();
        Users users = gson.fromJson(gson.toJson(usersBean), Users.class);
        Map<String, String> properties = users.getProperties();
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.remove("nearest_seller");
        properties.remove("nearest_pincode");
        properties.put("nearest_seller", response.getSeller_id());
        properties.put("nearest_pincode", pincode);
        properties.put("branch", req.getBranch());
        properties.put("branch_desc", req.getDesc());
        properties.put("semester", req.getSemester());
        properties.put("college", req.getCollege());
        users.setBranch(req.getBranch());
        users.setBranch_desc(isOtherBranch ? req.getBranch() : null);
        users.setSemester(semester.getAlias());
        users.setProperties(properties);
        users_Service.update(users.getId(), users, "/delivery/check");
        return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
    }

}
