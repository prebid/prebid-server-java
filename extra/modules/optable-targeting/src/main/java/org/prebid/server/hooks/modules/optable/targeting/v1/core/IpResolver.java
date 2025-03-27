package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IpResolver {

    private IpResolver() {
    }

    public static List<String> resolveIp(AuctionContext auctionContext) {
        final List<String> result = new ArrayList<>();

        final Optional<AuctionContext> auctionContextOpt = Optional.ofNullable(auctionContext);

        final Optional<Device> deviceOpt = auctionContextOpt
                .map(AuctionContext::getBidRequest)
                .map(BidRequest::getDevice);

        deviceOpt.map(Device::getIp).ifPresent(result::add);
        deviceOpt.map(Device::getIpv6).ifPresent(result::add);

        if (result.isEmpty()) {
            auctionContextOpt.map(AuctionContext::getPrivacyContext)
                    .map(PrivacyContext::getIpAddress)
                    .ifPresent(result::add);
        }

        return result;
    }
}
