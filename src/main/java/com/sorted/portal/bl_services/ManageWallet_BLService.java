package com.sorted.portal.bl_services;

import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.WalletEntity;
import com.sorted.commons.entity.service.WalletService;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.utils.CommonUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/wallet")
public class ManageWallet_BLService {

    private final WalletService walletService;

    @GetMapping("/fetch/balance")
    public BigDecimal fetch(HttpServletRequest servletRequest) {
        String userid = servletRequest.getHeader("req_user_id");
        if (!StringUtils.hasText(userid)) {
            throw new AccessDeniedException();
        }

        SEFilter filter = new SEFilter(SEFilterType.AND);

        filter.addClause(WhereClause.eq(WalletEntity.Fields.userId, userid));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        WalletEntity walletEntity = walletService.repoFindOne(filter);

        if (walletEntity == null) {
            return null;
        }
        return CommonUtils.paiseToRupee(walletEntity.getBalance());
    }
}
