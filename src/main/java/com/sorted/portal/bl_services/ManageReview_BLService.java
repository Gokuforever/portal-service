package com.sorted.portal.bl_services;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.beans.ProductReview;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.AddReview;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/review")
public class ManageReview_BLService {

    private final ProductService productService;
    private final Users_Service usersService;

    @PostMapping("/add")
    public SEResponse addReview(@RequestBody AddReview request, HttpServletRequest httpServletRequest) {

        CommonUtils.extractHeaders(httpServletRequest, request);
        UsersBean usersBean = usersService.validateUserForActivity(request.getReq_user_id(), Activity.PRODUCTS);
        Role role = usersBean.getRole();
        switch (role.getUser_type()) {
            case CUSTOMER:
                break;
            case GUEST:
                throw new CustomIllegalArgumentsException("Sign up or login to add a review.");
            default:
                throw new AccessDeniedException();
        }
        Preconditions.check(StringUtils.hasText(request.getProductId()), ResponseCode.MISSING_PRODUCT_ID);
        Preconditions.check(StringUtils.hasText(request.getTitle()), ResponseCode.MISSING_TITLE);
        Preconditions.check(StringUtils.hasText(request.getReview()), ResponseCode.MISSING_REVIEW_TEXT);
        Preconditions.check(request.getRating() > 0 && request.getRating() <= 5, ResponseCode.INVALID_RATING);

        Optional<Products> productsOptional = productService.findById(request.getProductId());
        if (productsOptional.isEmpty()) {
            throw new CustomIllegalArgumentsException(ResponseCode.PRODUCT_NOT_FOUND);
        }
        Products products = productsOptional.get();
        List<ProductReview> reviews = products.getReviews();
        ProductReview review = ProductReview.builder()
                .title(request.getTitle())
                .review(request.getReview())
                .rating(request.getRating())
                .userId(usersBean.getId())
                .userName(usersBean.getFirst_name() + " " + usersBean.getLast_name())
                .build();
        if (CollectionUtils.isEmpty(reviews)) {
            reviews = new ArrayList<>();
        }
        reviews.add(review);
        products.setReviews(reviews);
        productService.update(products.getId(), products, usersBean.getId());
        return SEResponse.getEmptySuccessResponse("Thanks for your review.");
    }
}
