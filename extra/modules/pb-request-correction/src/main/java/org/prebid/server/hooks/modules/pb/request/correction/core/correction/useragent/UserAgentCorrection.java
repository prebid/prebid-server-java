package org.prebid.server.hooks.modules.pb.request.correction.core.correction.useragent;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.Correction;

import java.util.regex.Pattern;

public class UserAgentCorrection implements Correction {

    private static final Pattern USER_AGENT_PATTERN = Pattern.compile("PrebidMobile/[0-9][^ ]*");

    @Override
    public BidRequest apply(BidRequest bidRequest) {
        return bidRequest.toBuilder()
                .device(correctDevice(bidRequest.getDevice()))
                .build();
    }

    private static Device correctDevice(Device device) {
        return device.toBuilder()
                .ua(USER_AGENT_PATTERN.matcher(device.getUa()).replaceAll(""))
                .build();
    }
}
