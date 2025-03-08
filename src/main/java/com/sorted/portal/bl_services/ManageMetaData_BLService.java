package com.sorted.portal.bl_services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.mongo.Product_Master;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.Product_Master_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.portal.request.beans.MetaDataReq;
import com.sorted.portal.response.beans.MetaData;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ManageMetaData_BLService {

    private final Category_MasterService categoryMasterService;
    private final Users_Service usersService;
    private final Product_Master_Service productMasterService;

    private final Map<String, List<Category_Master>> cache = new ConcurrentHashMap<>();
    private static final String CACHE_KEY = "CM_DATA";

    public ManageMetaData_BLService(Category_MasterService categoryMasterService,
                                    Users_Service usersService,
                                    Product_Master_Service productMasterService) {
        this.categoryMasterService = categoryMasterService;
        this.usersService = usersService;
        this.productMasterService = productMasterService;
    }

    @PostMapping("/cache/clear")
    public void clearCache() {
        cache.clear();
    }

    @PostMapping("/getMetaData")
    public SEResponse getMetaData(@RequestBody SERequest request) {

        log.info("getMetaData:: API started");
        MetaDataReq req = request.getGenericRequestDataObject(MetaDataReq.class);
        List<String> ids = req.getIds();
        MetaData data = new MetaData();

        // Consider adding a cache invalidation mechanism to prevent stale data
        List<Category_Master> categoryMasterData = cache.computeIfAbsent(CACHE_KEY, key -> this.getCategoryMasterData());
        if (!CollectionUtils.isEmpty(categoryMasterData)) {
            data.setCatagories(categoryMasterData);
        }

        SEFilter filterPM = new SEFilter(SEFilterType.AND);
        if (!CollectionUtils.isEmpty(ids)) {
            filterPM.addClause(WhereClause.in(BaseMongoEntity.Fields.id, ids));
        }
        filterPM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Product_Master> listPM = productMasterService.repoFind(filterPM);
        if (!CollectionUtils.isEmpty(listPM)) {
            data.setProducts(listPM);
        }
        data.setUpdated_at(LocalDateTime.now());

        log.info("getMetaData:: API ended");
        return SEResponse.getBasicSuccessResponseObject(data, ResponseCode.SUCCESSFUL);
    }

    private List<Category_Master> getCategoryMasterData() {
        SEFilter filterCM = new SEFilter(SEFilterType.AND);
        filterCM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterCM.addProjection(Category_Master.Fields.name, Category_Master.Fields.groups,
                Category_Master.Fields.category_code);
        return categoryMasterService.repoFind(filterCM);
    }

    @PostMapping("/getUserInfo")
    public SEResponse getUserInfo(HttpServletRequest httpServletRequest) {
        try {
            String req_user_id = httpServletRequest.getHeader("req_user_id");
            UsersBean usersBean = usersService.validateAndGetUserInfo(req_user_id);
            return SEResponse.getBasicSuccessResponseObject(usersBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/signup/verify:: exception occurred", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }
}
