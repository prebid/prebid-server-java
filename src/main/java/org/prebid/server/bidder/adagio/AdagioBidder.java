package org.prebid.server.bidder.adagio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adagio.ExtImpAdagio;
import org.prebid.server.proto.openrtb.ext.request.adkerneladn.ExtImpAdkernelAdn;
import org.prebid.server.util.HttpUtil;

import java.util.*;

public class AdagioBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdagio>> AGADIO_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdagio>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> validImps = request.getImp();
        final List<BidderError> errors = new ArrayList<>();

        final Map<Imp, ExtImpAdagio> impWithExts = getAndValidateImpExt(validImps, errors);
        final Map<ExtImpAdkernelAdn, List<Imp>> pubToImps = dispatchImpressions(impWithExts, errors);
        if (MapUtils.isEmpty(pubToImps)) {
            return Result.withErrors(errors);
        }

        return Result.of(buildAdapterRequests(bidRequest, pubToImps), errors);
    }


    private static Map<ExtImpAdkernelAdn, List<Imp>> dispatchImpressions(Map<Imp, ExtImpAdkernelAdn> impsWithExts,
                                                                         List<BidderError> errors) {
        final Map<ExtImpAdkernelAdn, List<Imp>> result = new HashMap<>();

        for (Imp key : impsWithExts.keySet()) {
            final Imp imp;
            try {
                imp = compatImpression(key);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            final ExtImpAdkernelAdn impExt = impsWithExts.get(key);
            result.putIfAbsent(impExt, new ArrayList<>());
            result.get(impExt).add(imp);
        }

        return result;
    }

    private Map<Imp, ExtImpAdagio> getAndValidateImpExt(List<Imp> validImps, List<BidderError> errors) {
        final Map<Imp, ExtImpAdkernelAdn> validImpsWithExts = new HashMap<>();
        for (Imp imp : imps) {
            try {
                validateImp(imp);
                validImpsWithExts.put(imp, parseAndValidateAdkernelAdnExt(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return validImpsWithExts;
    }

    private ExtImpAdkernelAdn parseAndValidateAgadioExt(Imp imp) {
        final ExtImpAdagio agadioExt;
        try {
            agadioExt = mapper.mapper().convertValue(imp.getExt(), AGADIO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (adkernelAdnExt.getPubId() == null || adkernelAdnExt.getPubId() < 1) {
            throw new PreBidException(String.format("Invalid pubId value. Ignoring imp id=%s", imp.getId()));
        }
        return adkernelAdnExt;
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format("Invalid imp with id=%s. Expected imp.banner or imp.video",
                    imp.getId()));
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }

    public AdagioBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }
}
