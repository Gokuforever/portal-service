package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.EducationCategoryBean;
import com.sorted.commons.beans.EducationCategoryField;
import com.sorted.commons.beans.NearestSellerRes;
import com.sorted.commons.entity.mongo.EducationCategories;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.EducationCategoriesService;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.FormDataBean;
import com.sorted.portal.service.EducationDetailsValidationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequiredArgsConstructor
public class ManagePopUp_BLService {

    private final EducationCategoriesService educationCategoriesService;
    private final EducationDetailsValidationService validationService;
    private final Users_Service usersService;
    private final RoleService roleService;
    private final PorterUtility porterUtility;

    @GetMapping("/formConfig")
    public List<EducationCategoryBean> getPopUpDetails() {
        log.info("Fetching all popup form data");
        List<EducationCategoryBean> result = educationCategoriesService.repoFindAll().stream()
                .map(EducationCategoryBean::new)
                .toList();
        log.debug("Fetched {} education categories", result.size());
        return result;
    }

    @PostMapping("/form-data/submit")
    public SEResponse updatePopUpDetails(@RequestBody SERequest request, HttpServletRequest httpServletRequest) throws JsonProcessingException {
        log.info("Received request to update popup details");

        FormDataBean req = request.getGenericRequestDataObject(FormDataBean.class);
        log.debug("Request data: {}", req);
        CommonUtils.extractHeaders(httpServletRequest, req);

        log.debug("Looking up user with ID: {}", req.getReq_user_id());
        Users user = usersService.findById(req.getReq_user_id())
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", req.getReq_user_id());
                    return new AccessDeniedException();
                });
        log.debug("Checking user role for user: {}", user.getId());
        Role role = roleService.findById(user.getRole_id())
                .orElseThrow(() -> {
                    log.error("Role not found for role ID: {}", user.getRole_id());
                    return new AccessDeniedException();
                });

        if (!role.getUser_type().equals(UserType.GUEST)) {
            log.warn("Access denied for non-GUEST user: {}", user.getId());
            throw new AccessDeniedException();
        }
        log.debug("Validating request parameters");
        Preconditions.check(StringUtils.hasText(req.getPincode()), ResponseCode.MANDATE_PINCODE);
        log.debug("Pincode validation successful");

        EducationCategoryBean educationDetails = req.getEducationDetails();
        if (educationDetails != null) {
            validationService.validate(educationDetails);
            user.setEducationDetails(educationDetails);
            log.debug("Updating user education details for user: {}", user.getId());
            usersService.update(user.getId(), user, user.getId());
            log.info("Successfully updated education details for user: {}", user.getId());
        }

        log.debug("Finding nearest seller for pincode: {}", req.getPincode());
        NearestSellerRes response = porterUtility.getNearestSeller(req.getPincode(), null, user.getMobile_no(), user.getId());
        log.debug("Found nearest seller: {}", response.getSeller_id());
        log.debug("Updating user's nearest seller information");
        user.setNearestSeller(response.getSeller_id());
        user.setNearestPincode(req.getPincode());
        usersService.update(user.getId(), user, user.getId());
        log.info("Successfully updated user {} with nearest seller: {}", user.getId(), response.getSeller_id());

        SEResponse successResponse = SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
        log.debug("Returning success response for user: {}", user.getId());
        return successResponse;
    }

}
