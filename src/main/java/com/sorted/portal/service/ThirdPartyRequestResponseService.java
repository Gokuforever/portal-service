package com.sorted.portal.service;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.Third_Party_Api;
import com.sorted.commons.entity.service.Third_Party_Api_Service;
import com.sorted.commons.entity.service.Transaction_Req_Response_Service;
import com.sorted.portal.enums.RequestType;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyRequestResponseService {

    private final Third_Party_Api_Service thirdPartyApiService;

    public ThirdPartyRequestResponseService(Third_Party_Api_Service thirdPartyApiService) {
        this.thirdPartyApiService = thirdPartyApiService;
    }

    public Third_Party_Api register(Object req, @NonNull RequestType requestType) {
        Third_Party_Api thirdPartyApi = new Third_Party_Api();
        thirdPartyApi.setRaw_request(req.toString());
        thirdPartyApi.setRequest_type(requestType.name());
        return thirdPartyApiService.create(thirdPartyApi, Defaults.SYSTEM_ADMIN);
    }

    public void updateResponse(Third_Party_Api thirdPartyApi, Object res){
        thirdPartyApi.setRaw_response(res.toString());
        thirdPartyApiService.update(thirdPartyApi.getId(), thirdPartyApi, Defaults.SYSTEM_ADMIN);
    }

    public void registerException(Third_Party_Api thirdPartyApi, String errorMessage){
        thirdPartyApi.setRaw_response(errorMessage);
        thirdPartyApi.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        thirdPartyApiService.update(thirdPartyApi.getId(), thirdPartyApi, Defaults.SYSTEM_ADMIN);
    }


}
