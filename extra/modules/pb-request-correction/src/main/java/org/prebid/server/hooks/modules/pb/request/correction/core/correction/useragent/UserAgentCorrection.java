package org.prebid.server.hooks.modules.pb.request.correction.core.correction.useragent;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.prebid.server.hooks.modules.pb.request.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.Correction;

public class UserAgentCorrection implements Correction {

    private static final String USER_AGENT_PATTERN = "PrebidMobile/[0-9][^ ]*";

    @Override
    public BidRequest apply(BidRequest bidRequest) {
        return bidRequest.toBuilder()
                .device(correctDevice(bidRequest.getDevice()))
                .build();
    }

    private static Device correctDevice(Device device) {
        return device.toBuilder()
                .ua(device.getUa().replaceAll(USER_AGENT_PATTERN, ""))
                .build();
    }
}
