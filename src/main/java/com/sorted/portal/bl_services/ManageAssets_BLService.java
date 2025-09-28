package com.sorted.portal.bl_services;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.AssetsEntity;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.service.AssetsService;
import com.sorted.commons.enums.AssetType;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.utils.AwsS3Service;
import com.sorted.portal.request.beans.UploadAssetBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestController
@RequestMapping("/assets")
public class ManageAssets_BLService {

    private final AwsS3Service awsS3Service;
    private final AssetsService assetsService;

    @PostMapping("/promo-banner/create")
    public void promoBannerCreate(@RequestBody UploadAssetBean request) throws IOException {

        log.info("Request: {}", request);

        String cdnUrl = awsS3Service.uploadPhoto(request.getBytes(), request.getContentType(), request.getFileName());

        int existingOrder = 0;

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(AssetsEntity.Fields.type, AssetType.HOME_PROMO_BANNER.name()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<AssetsEntity> assetsEntities = assetsService.repoFind(filter);
        if (!CollectionUtils.isEmpty(assetsEntities)) {
            existingOrder = assetsEntities.stream()
                    .sorted(Comparator.comparing(AssetsEntity::getOrder).reversed())
                    .toList().get(0).getOrder();
        }

        AssetsEntity assets = new AssetsEntity();
        assets.setType(AssetType.HOME_PROMO_BANNER);
        assets.setUrl(cdnUrl);
        assets.setOrder(existingOrder + 1);
        assets.setAltText(request.getAltText());

        assetsService.create(assets, Defaults.RETOOL);
    }

}
