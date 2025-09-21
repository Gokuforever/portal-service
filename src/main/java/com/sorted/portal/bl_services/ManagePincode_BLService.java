package com.sorted.portal.bl_services;

import com.google.gson.Gson;
import com.sorted.commons.beans.NearestSellerRes;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.Address;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Seller;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Address_Service;
import com.sorted.commons.entity.service.Seller_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.Semester;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.porter.req.beans.GetQuoteRequest;
import com.sorted.commons.porter.res.beans.GetQuoteResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.GsonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.CheckForDelivery;
import com.sorted.portal.request.beans.NearestSellerReq;
import com.sorted.portal.service.NearestSellerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class ManagePincode_BLService {

    private final NearestSellerService nearestSellerService;
    private final Users_Service users_Service;
    private final PorterUtility porterUtility;
    private final Seller_Service sellerService;
    private final Address_Service addressService;
    private final Users_Service userService;

    @Value("${se.default.seller:68711a63a2dcdf55ed170972}")
    private String defaultSeller;

    @PostMapping("/delivery/check")
    public SEResponse checkDelivery(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {

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
        NearestSellerRes response = nearestSellerService.getNearestSeller(pincode, usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getId());
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

    @PostMapping("/deliverable")
    public boolean deliverable(@RequestBody CheckForDelivery request, HttpServletRequest httpServletRequest) {

        String userid = httpServletRequest.getHeader("req_user_id");
        if (!StringUtils.hasText(userid)) {
            throw new AccessDeniedException();
        }
        UsersBean usersBean = userService.validateUserForActivity(userid, Activity.MANAGE_ADDRESS);
        if (!usersBean.getRole().getUser_type().equals(UserType.CUSTOMER))
            throw new AccessDeniedException();

        if (0 <= BigDecimal.ZERO.compareTo(request.lng()) && 0 <= BigDecimal.ZERO.compareTo(request.lat()))
            return false;

        Optional<Seller> optionalSeller = sellerService.findById(defaultSeller);
        if (optionalSeller.isEmpty()) return false;

        Seller seller = optionalSeller.get();

        Optional<Address> addressOptional = addressService.findById(seller.getAddress_id());
        if (addressOptional.isEmpty()) return false;

        Address pickupAddress = new Address();
        pickupAddress.setLat(request.lat());
        pickupAddress.setLng(request.lng());
        GetQuoteRequest getQuoteRequest = porterUtility.buildGetQuoteRequest(pickupAddress, addressOptional.get(), usersBean.getMobile_no(), StringUtils.hasText(usersBean.getFirst_name()) ? usersBean.getFirst_name() : "User");
        GetQuoteResponse deliveryQuote = porterUtility.getDeliveryQuote(getQuoteRequest);
        return deliveryQuote != null && deliveryQuote.getVehicle() != null;
    }

}
