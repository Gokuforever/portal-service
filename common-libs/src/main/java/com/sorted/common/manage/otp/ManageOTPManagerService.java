package com.sorted.common.manage.otp;

import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Otp;
import com.sorted.common.entity.service.Otp_Service;
import com.sorted.common.enums.ProcessType;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManageOTPManagerService {

    private final ManageOtp manageOtp;

    private final Otp_Service otp_Service;

    public String send(@NonNull String mobile_number, @NonNull ProcessType process_type, String cud_by) {
        return manageOtp.generateAndSaveOtp(mobile_number, process_type, cud_by);
    }

    public String resendOtp(@NonNull ProcessType process, @NonNull String uuid) {
        SEFilter filterO = new SEFilter(SEFilterType.AND);
        filterO.addClause(WhereClause.eq(Otp.Fields.process_type, process.name()));
        filterO.addClause(WhereClause.eq(Otp.Fields.status, true));
        filterO.addClause(WhereClause.eq(Otp.Fields.is_verified, false));
        filterO.addClause(WhereClause.eq(Otp.Fields.uuid, uuid));
        filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Otp oldOtp = otp_Service.repoFindOne(filterO);
        if (oldOtp == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_RESEND_REQUEST);
        }
        oldOtp.setStatus(false);
        otp_Service.update(oldOtp.getId(), oldOtp, Defaults.RESEND);
        return this.send(oldOtp.getMobile_no(), process, Defaults.RESEND);
    }
}
