package com.sorted.portal.webhooks;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.enums.WebhookType;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.helper.WebhookTraceHelper;
import com.sorted.commons.porter.req.beans.PorterWebhookBean;
import com.sorted.commons.utils.PorterUtility;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class PorterWebhookController {

    private final WebhookTraceHelper webhookTraceHelper;
    private final PorterUtility porterUtility;

    @Value("${se.porter.config.auth.token:cd659135-c032-4f1e-80fa-ff78db812511}")
    private String porterToken;

    @PostMapping("/porter/order_update")
    public SEResponse porterOrderUpdate(@RequestBody PorterWebhookBean response, HttpServletRequest httpServletRequest) {
        String api_key = httpServletRequest.getHeader("x-api-key");
        if (!StringUtils.hasText(api_key) || !api_key.equals(porterToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized access");
        }
        return webhookTraceHelper.runWithTrace(
                WebhookType.PORTED_ORDER_UPDATE,
                response,
                Defaults.PORTER_ORDER_UPDATE_WEBHOOK,
                () -> porterUtility.handleWebhookResponse(response)
        );
    }
}
