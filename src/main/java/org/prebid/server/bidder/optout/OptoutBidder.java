package org.prebid.server.bidder.optout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.optout.ExtImpOptout;
import org.prebid.server.util.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OptoutBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOptout>> TYPE_REFERENCE = new TypeReference<>(){
    };
    private static final String DEFAULT_BID_CURRENCY = "EUR";
    private static final Integer DEFAULT_TTL = 300;
    private static final Integer OPTOUT_VENDOR_ID = 227;
    private static final String MATCHER_NAME = "PREBID";

    private final Uri endpointUrl;
    private final JacksonMapper mapper;

    public OptoutBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Uri.of(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        for (Imp imp : request.getImp()) {
            if (imp.getBanner() == null) {

            }
        }
        return null;
    }

    private ExtImpOptout parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private List<AdSize> getAllowedSizes(Imp imp) {
        Banner banner = imp.getBanner();
        Set<AdSize> uniqueSizes = new LinkedHashSet<>();

        if (banner.getW() != null && banner.getH() != null) {
            uniqueSizes.add(new AdSize(banner.getW(), banner.getH()));
        }

        if (banner.getFormat() != null) {
            for (Format format : banner.getFormat()) {
                if (format != null && format.getW() != null && format.getH() != null) {
                    uniqueSizes.add(new AdSize(format.getW(), format.getH()));
                }
            }
        }

        return new ArrayList<>(uniqueSizes);
    }

    

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }
}
record AdSize(Integer w, Integer h){
}
