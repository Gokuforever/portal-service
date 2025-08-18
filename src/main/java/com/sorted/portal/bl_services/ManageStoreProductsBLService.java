package com.sorted.portal.bl_services;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.assisting.beans.ProductDetailsBeanList;
import com.sorted.portal.request.beans.FindProductBean;
import com.sorted.portal.service.StoreProductService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/store/product")
@RestController
@RequiredArgsConstructor
public class ManageStoreProductsBLService {

    private final Users_Service usersService;
    private final StoreProductService storeProductService;

    @PostMapping("/find")
    public List<ProductDetailsBeanList> getProducts(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        UsersBean usersBean = usersService.validateUserForActivity(req.getReq_user_id(), Activity.PRODUCTS,
                Activity.INVENTORY_MANAGEMENT);
        switch (usersBean.getRole().getUser_type()) {
            case CUSTOMER, GUEST:
                break;
            default:
                throw new AccessDeniedException();
        }

        return storeProductService.getProductDetailsBeanLists(req, usersBean);
    }
}
