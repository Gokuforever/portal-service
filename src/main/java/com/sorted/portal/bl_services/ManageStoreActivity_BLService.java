package com.sorted.portal.bl_services;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.service.StoreActivityService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.SEResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ManageStoreActivity_BLService {

    private final StoreActivityService storeActivityService;
    private final Users_Service users_Service;

    @PostMapping("/store/open")
    public SEResponse openStore(HttpServletRequest request) {
        UsersBean usersBean = validateUserForActivity(request, Activity.OPEN_STORE);
        storeActivityService.openStore(usersBean.getSeller().getId(), usersBean.getId());
        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    @PostMapping("/store/close")
    public SEResponse openClose(HttpServletRequest request) {
        UsersBean usersBean = validateUserForActivity(request, Activity.CLOSE_STORE);
        storeActivityService.closeStore(usersBean.getSeller().getId(), usersBean.getId());
        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    @GetMapping("/store/status")
    public boolean getStoreStatus(HttpServletRequest request) {
        UsersBean usersBean = validateUserForActivity(request, Activity.USER_PROFILE);
        return storeActivityService.isStoreOperational(usersBean.getSeller().getId());
    }

    @NotNull
    private UsersBean validateUserForActivity(HttpServletRequest request, Activity activity) {
        String req_user_id = request.getHeader("req_user_id");
        if (!StringUtils.hasText(req_user_id)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
        UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, activity);
        if (usersBean.getRole().getUser_type() != UserType.SELLER) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
        return usersBean;
    }
}
