package com.sorted.portal.bl_services;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.mongo.Product_Master;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.Product_Master_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.portal.request.beans.MetaDataReq;
import com.sorted.portal.response.beans.MetaData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageMetaData_BLService {

    private final Category_MasterService categoryMasterService;
    private final Users_Service usersService;
    private final Product_Master_Service productMasterService;

    private final Map<String, List<Category_Master>> categoryCache = new ConcurrentHashMap<>();
    private final Map<String, List<Product_Master>> productCache = new ConcurrentHashMap<>();
    private static final String CM_CACHE_KEY = "CM_DATA";
    private static final String PM_CACHE_KEY = "PM_DATA";


    @PostMapping("/cache/clear")
    public void clearCache() {
        categoryCache.clear();
        productCache.clear();
    }

    @PostMapping("/getMetaData")
    public SEResponse getMetaData(@RequestBody SERequest request) {

        log.info("getMetaData:: API started");
        MetaDataReq req = request.getGenericRequestDataObject(MetaDataReq.class);
        List<String> ids = req.getIds();
        MetaData data = new MetaData();

        List<Category_Master> categoryMasterData = this.getCategoryMasterData();
//        List<Category_Master> categoryMasterData = categoryCache.computeIfAbsent(CM_CACHE_KEY, key -> this.getCategoryMasterData());
        if (!CollectionUtils.isEmpty(categoryMasterData)) {
            data.setCatagories(categoryMasterData);
        }

        List<Product_Master> listPM = this.getProductMasters();
//        List<Product_Master> listPM = productCache.computeIfAbsent(PM_CACHE_KEY, key -> this.getProductMasters());
        if (!CollectionUtils.isEmpty(listPM)) {
            data.setProducts(listPM);
        }
        data.setUpdated_at(LocalDateTime.now());

        log.info("getMetaData:: API ended");
        return SEResponse.getBasicSuccessResponseObject(data, ResponseCode.SUCCESSFUL);
    }

    private List<Product_Master> getProductMasters() {
        SEFilter filterPM = new SEFilter(SEFilterType.AND);
        filterPM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterPM.setOrderBy(new AggregationFilter.OrderBy(BaseMongoEntity.Fields.id, AggregationFilter.SortOrder.DESC));
        filterPM.addProjection(Product_Master.Fields.name, Product_Master.Fields.catagory_id, Product_Master.Fields.sub_categories, Product_Master.Fields.cdn_url, Product_Master.Fields.group_id);
        return productMasterService.repoFind(filterPM);
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
