package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;

public class PropertiesValidator {

    private PropertiesValidator() {
    }

    public static boolean isValid(OptableTargetingProperties properties) {
        return StringUtils.isNotEmpty(properties.getOrigin()) && StringUtils.isNotEmpty(properties.getTenant());
    }

    public static boolean isTrafficSourceValid(BidRequest bidRequest, OptableTargetingProperties properties) {
        return (Boolean.TRUE.equals(properties.getEnrichWeb()) && bidRequest.getSite() != null)
                || (Boolean.TRUE.equals(properties.getEnrichApp()) && bidRequest.getApp() != null);
    }
}
