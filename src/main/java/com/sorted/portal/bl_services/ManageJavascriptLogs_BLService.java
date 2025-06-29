package com.sorted.portal.bl_services;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.JavascriptLogTrace;
import com.sorted.commons.entity.service.JavascriptLogTraceService;
import com.sorted.commons.enums.LogType;
import com.sorted.commons.helper.SERequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ManageJavascriptLogs_BLService {

    private final JavascriptLogTraceService javascriptLogTraceService;

    public ManageJavascriptLogs_BLService(JavascriptLogTraceService javascriptLogTraceService) {
        this.javascriptLogTraceService = javascriptLogTraceService;
    }

    @PostMapping("/createErrorLogTrace")
    public void createErrorLogTrace(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        storeLogTrace(request, httpServletRequest, LogType.ERROR, "createErrorLogTrace");
    }

    @PostMapping("/createInfoLogTrace")
    public void createInfoLogTrace(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        storeLogTrace(request, httpServletRequest, LogType.INFO, "createInfoLogTrace");
    }

    @PostMapping("/createDebugLogTrace")
    public void createDebugLogTrace(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        storeLogTrace(request, httpServletRequest, LogType.DEBUG, "createDebugLogTrace");
    }

    /**
     * Helper method to store JavaScript log traces with the given log type
     *
     * @param request            The service request containing log data
     * @param httpServletRequest The HTTP servlet request for getting user information
     * @param logType            The type of log (ERROR, INFO, DEBUG)
     * @param methodName         The name of the calling method for logging purposes
     */
    private void storeLogTrace(SERequest request, HttpServletRequest httpServletRequest,
                               LogType logType, String methodName) {
        log.info("{}: Creating {} log trace", methodName, logType);

        try {
            String req_user_id = httpServletRequest.getHeader("req_user_id");
            log.debug("{}: User ID from request: {}", methodName, req_user_id);

            JavascriptLogTrace logTrace = JavascriptLogTrace.builder()
                    .data(request)
                    .userId(req_user_id)
                    .logType(logType)
                    .build();

            log.debug("{}: Created log trace object for app: {}", methodName, Defaults.REACT_APP);

            javascriptLogTraceService.create(logTrace, Defaults.REACT_APP);
            log.info("{}: Successfully saved {} log trace for user: {}",
                    methodName, logType, req_user_id);

        } catch (Exception e) {
            log.error("{}: Failed to create {} log trace: {}",
                    methodName, logType, e.getMessage(), e);
            throw e;
        }
    }
}
