package com.sorted.portal.bl_services;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.utils.Preconditions;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ManageCategory_BLService {

    private final Category_MasterService categoryMasterService;

    @PostMapping("/category/upsert")
    public void update(@RequestBody List<Category_Master> categoryMasters) {
        Preconditions.check(CollectionUtils.isNotEmpty(categoryMasters), ResponseCode.MANDATE_CATEGORY);
        categoryMasters.forEach(categoryMaster -> {
            Preconditions.check(StringUtils.hasText(categoryMaster.getId()), ResponseCode.MISSING_ID);
            Preconditions.check(StringUtils.hasText(categoryMaster.getName()), ResponseCode.MISSING_CATEGORY_NAME);
            Preconditions.check(CollectionUtils.isEmpty(categoryMaster.getGroups()), ResponseCode.MISSING_GROUPS);
        });

        List<String> ids = categoryMasters.stream().filter(e -> StringUtils.hasText(e.getId())).map(BaseMongoEntity::getId).toList();

        Map<String, Category_Master> mapCM = categoryMasters.stream().filter(e -> StringUtils.hasText(e.getId())).collect(Collectors.toMap(Category_Master::getId, Function.identity()));
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, ids));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Category_Master> categoryMasterList = categoryMasterService.repoFind(filter);
        Preconditions.check(CollectionUtils.isNotEmpty(categoryMasterList), ResponseCode.NO_RECORD);

        categoryMasters.forEach(categoryMaster -> {
            if (StringUtils.hasText(categoryMaster.getId())) {
                Category_Master category_master = mapCM.get(categoryMaster.getId());
                category_master.setName(categoryMaster.getName());
                category_master.setGroups(categoryMaster.getGroups());
                categoryMasterService.update(category_master.getId(), category_master, Defaults.SYSTEM_ADMIN);
                return;
            }
            categoryMasterService.create(categoryMaster, Defaults.SYSTEM_ADMIN);
        });
    }

    @DeleteMapping("/category/delete")
    public void delete(@RequestParam String id) {
        Preconditions.check(StringUtils.hasText(id), ResponseCode.MISSING_ID);
        categoryMasterService.deleteOne(id, Defaults.SYSTEM_ADMIN);
    }
}
