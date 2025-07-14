package com.sorted.portal.bl_services;

import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.service.Category_MasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ManageCategory_BLService {

    private final Category_MasterService categoryMasterService;

    @GetMapping("/category-master")
    public List<Category_Master> categoryMasterData() {
        return categoryMasterService.repoFindAll();
    }
}
