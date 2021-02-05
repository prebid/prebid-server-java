package org.prebid.server.bidder.revcontent;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

public class RevcontentBidder extends OpenrtbBidder<Void> {

    public RevcontentBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, Void.class, mapper);
    }

    @Override
    protected void validateRequest(BidRequest bidRequest) throws PreBidException {
        final App app = bidRequest.getApp();
        final Site site = bidRequest.getSite();
        final boolean hasAppName = app != null && StringUtils.isNotBlank(app.getName());
        final boolean hasSiteDomain = site != null && StringUtils.isNotBlank(site.getDomain());
        if (!hasAppName && !hasSiteDomain) {
            throw new PreBidException("Impression is missing app name or site domain, and must contain one.");
        }
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        // native: {"ver":"1.1","assets":...
        // banner: <div id='rtb-widget...
        final String bidAdm = bid.getAdm();
        return StringUtils.isNotBlank(bidAdm) && bidAdm.charAt(0) == '<'
                ? BidType.banner
                : BidType.xNative;
    }
}

