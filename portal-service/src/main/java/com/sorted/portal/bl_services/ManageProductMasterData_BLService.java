package com.sorted.portal.bl_services;

import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Category_Master;
import com.sorted.common.entity.service.Category_MasterService;
import com.sorted.common.entity.service.Product_Master_Service;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.helper.SERequest;
import com.sorted.portal.assisting.beans.ProductMasterDataBean;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ManageProductMasterData_BLService {

    private final Product_Master_Service productMasterService;
    private final Category_MasterService categoryMasterService;

    @PostMapping("/product-master/create")
    public boolean create(@RequestBody SERequest request) {

        ProductMasterDataBean req = request.getGenericRequestDataObject(ProductMasterDataBean.class);
        productMasterService.bulkCreate(req.getData(), Defaults.SYSTEM_ADMIN);
        return true;
    }

    @GetMapping("/category-master")
    public List<Category_Master> categoryMaster() {
        return getCategoryMasterData();
    }

    private List<Category_Master> getCategoryMasterData() {
        AggregationFilter.SEFilter filterCM = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterCM.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterCM.addProjection(Category_Master.Fields.name, Category_Master.Fields.groups,
                Category_Master.Fields.category_code);
        return categoryMasterService.repoFind(filterCM);
    }
}
