package org.prebid.server.bidder.revcontent;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.List;

public class RevcontentBidder extends OpenrtbBidder<Void> {

    public RevcontentBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, Void.class, mapper);
    }

    @Override
    protected void validateRequest(BidRequest bidRequest) throws PreBidException {
        final App app = bidRequest.getApp();
        final Site site = bidRequest.getSite();
        if ((app == null || StringUtils.isBlank(app.getName()))
                && (site == null || StringUtils.isBlank(site.getDomain()))) {
            throw new PreBidException("Impression is missing app name or site domain, and must contain one.");
        }
    }

    @Override
    protected MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        final String bidAdm = bid.getAdm();
        return StringUtils.isNotBlank(bidAdm) && bidAdm.charAt(0) == '<'
                ? BidType.banner : BidType.xNative;
    }
}

