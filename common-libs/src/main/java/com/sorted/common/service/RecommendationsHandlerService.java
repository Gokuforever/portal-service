package com.sorted.common.service;

import com.sorted.common.beans.Recommendations;
import com.sorted.common.beans.RegisterRecommendationsBean;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Products;
import com.sorted.common.entity.mongo.RecommendationsEntity;
import com.sorted.common.entity.mongo.RecommendersEntity;
import com.sorted.common.entity.service.ProductService;
import com.sorted.common.entity.service.RecommendationsService;
import com.sorted.common.entity.service.RecommendersService;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.utils.Preconditions;
import com.sorted.common.utils.SERegExpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationsHandlerService {

    private final RecommendationsService recommendationsService;
    private final RecommendersService recommendersService;
    private final ProductService productService;

    public RecommendersEntity searchRecommender(String phoneNo) {
        if (!StringUtils.hasText(phoneNo)) {
            return null;
        }
        Preconditions.check(SERegExpUtils.isMobileNo(phoneNo), ResponseCode.INVALID_PHONE);
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(RecommendersEntity.Fields.phoneNo, phoneNo));
        List<RecommendersEntity> recommenders = recommendersService.repoFind(filter);
        if (!CollectionUtils.isEmpty(recommenders)) {
            return recommenders.get(0);
        }
        return null;
    }

    public void registerRecommendation(RegisterRecommendationsBean request) {
        Preconditions.check(Objects.nonNull(request), ResponseCode.INVALID_REQ);
        Preconditions.check(Objects.nonNull(request.getType()), ResponseCode.MISSING_RECOMMENDER_TYPE);
        Preconditions.check(Objects.nonNull(request.getDetails()), ResponseCode.MISSING_RECOMMENDER_DETAILS);
        Preconditions.check(StringUtils.hasText(request.getDetails().getPhone()), ResponseCode.MANDATE_PHONE);
        Preconditions.check(SERegExpUtils.isMobileNo(request.getDetails().getPhone()), ResponseCode.INVALID_PHONE);
        Preconditions.check(StringUtils.hasText(request.getDetails().getEmail()), ResponseCode.MISSING_EI);
        Preconditions.check(StringUtils.hasText(request.getDetails().getFirstName()), ResponseCode.MANDATE_FIRST_NAME);
        Preconditions.check(StringUtils.hasText(request.getDetails().getLastName()), ResponseCode.MANDATE_LAST_NAME);

        List<Recommendations> recommendations = request.getRecommendations();
        Preconditions.check(!CollectionUtils.isEmpty(recommendations), ResponseCode.MISSING_RECOMMENDATIONS);
        recommendations.forEach(e -> {
            Preconditions.check(StringUtils.hasText(e.getProductId()), ResponseCode.MANDATE_PRODUCT_ID);
            Preconditions.check(StringUtils.hasText(e.getTitle()), ResponseCode.MANDATE_TITLE);
            Preconditions.check(StringUtils.hasText(e.getText()), ResponseCode.MANDATE_TEXT);
            Preconditions.check(e.getRating() > 0 && e.getRating() <= 5, ResponseCode.INVALID_RATING);
        });

        Map<String, Recommendations> recommendationsMap = recommendations.stream().collect(Collectors.toMap(Recommendations::getProductId, e -> e));

        List<String> productIds = recommendations.stream().map(Recommendations::getProductId).toList();
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Products> products = productService.repoFind(filter);

        if (CollectionUtils.isEmpty(products)) {
            return;
        }
        String recommenderId = null;
        if (StringUtils.hasText(request.getRecommenderId())) {
            Optional<RecommendersEntity> optionalRecommender = recommendersService.findById(request.getRecommenderId());
            if (optionalRecommender.isPresent()) {
                RecommendersEntity recommendersEntity = optionalRecommender.get();

                recommendersEntity.setFirstName(request.getDetails().getFirstName());
                recommendersEntity.setLastName(request.getDetails().getLastName());
                recommendersEntity.setPhotoUrl(request.getDetails().getPhotoUrl());
                recommendersEntity.setBio(request.getDetails().getBio());
                switch (request.getType()) {
                    case INDUSTRY_EXPERT:
                        recommendersEntity.setIndustryExpertInfo(request.getIndustryExpertInfo());
                        break;
                    case PROFESSOR:
                        recommendersEntity.setProfessorInfo(request.getProfessorInfo());
                        break;
                    case STUDENT:
                        recommendersEntity.setStudentInfo(request.getStudentInfo());
                        break;
                }
                recommenderId = recommendersEntity.getId();
            }
        } else {
            RecommendersEntity recommendersEntity = getRecommendersEntity(request);
            recommendersEntity = recommendersService.create(recommendersEntity, Defaults.SYSTEM_ADMIN);
            recommenderId = recommendersEntity.getId();
        }

        List<RecommendationsEntity> list = new ArrayList<>();
        for (Products product : products) {
            Recommendations recommendation = recommendationsMap.getOrDefault(product.getId(), null);
            if (recommendation == null) {
                continue;
            }
            RecommendationsEntity recommendationsEntity = RecommendationsEntity.builder().productId(product.getId()).title(recommendation.getTitle()).text(recommendation.getText()).rating(recommendation.getRating()).recommenderId(recommenderId).build();
            list.add(recommendationsEntity);
        }

        recommendationsService.bulkCreate(list, Defaults.SYSTEM_ADMIN);
    }

    private static RecommendersEntity getRecommendersEntity(RegisterRecommendationsBean request) {
        RecommendersEntity recommendersEntity = RecommendersEntity.builder()
                .firstName(request.getDetails().getFirstName())
                .lastName(request.getDetails().getLastName())
                .email(request.getDetails().getEmail())
                .phoneNo(request.getDetails().getPhone())
                .photoUrl(request.getDetails().getPhotoUrl())
                .bio(request.getDetails().getBio())
                .type(request.getType())
                .build();
        switch (request.getType()) {
            case INDUSTRY_EXPERT:
                recommendersEntity.setIndustryExpertInfo(request.getIndustryExpertInfo());
                break;
            case PROFESSOR:
                recommendersEntity.setProfessorInfo(request.getProfessorInfo());
                break;
            case STUDENT:
                recommendersEntity.setStudentInfo(request.getStudentInfo());
                break;
        }
        return recommendersEntity;
    }
}
