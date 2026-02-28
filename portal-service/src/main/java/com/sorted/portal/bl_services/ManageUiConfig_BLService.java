package com.sorted.portal.bl_services;

import com.sorted.common.entity.mongo.Ui_Config;
import com.sorted.common.entity.service.Ui_Config_Service;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ManageUiConfig_BLService {

    private final Ui_Config_Service ui_Config_Service;

    @GetMapping("/ui_config")
    public Ui_Config getUiConfig(@RequestParam String page) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Ui_Config.Fields.page, page));
        return ui_Config_Service.repoFindOne(filter);
    }
}
