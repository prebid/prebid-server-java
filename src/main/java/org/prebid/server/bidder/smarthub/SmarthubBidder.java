package org.prebid.server.bidder.smarthub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.*;
import org.prebid.server.bidder.smarthub.model.SmarthubImpExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adoppler.ExtImpAdoppler;
import org.prebid.server.proto.openrtb.ext.request.between.ExtImpBetween;
import org.prebid.server.proto.openrtb.ext.request.bidmachine.ExtImpBidmachine;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SmarthubBidder implements Bidder<BidRequest> {
    private final String ADAPTER_VER = "1.0.0";
    private final String endpointTemplate;
    private final JacksonMapper mapper;
    private static final TypeReference<ExtPrebid<?, SmarthubImpExt>> SMARTHUB_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, SmarthubImpExt>>() {
            };

    public SmarthubBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = request.getImp();
        final SmarthubImpExt smarthubImpExt = null;
        try {
                smarthubImpExt = parseAndValidateImpExt(imps.get(0));

            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }

        return Result.withValues(createRequest(request, smarthubImpExt, imps), errors);
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, SmarthubImpExt smarthubImpExt, List<Imp> imps) {
        final BidRequest outgoingRequest = request.toBuilder().imp(imps).build();

        return
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(buildEndpointUrl(smarthubImpExt))
                        .headers(resolveHeaders())
                        .body(mapper.encode(outgoingRequest))
                        .build();
    }

    private MultiMap resolveHeaders() {
        final MultiMap headers = HttpUtil.headers();

        headers.add(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
        headers.add(HttpUtil.ACCEPT_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
        headers.add("Prebid-Adapter-Ver", ADAPTER_VER);

        return headers;
    }

    private String buildEndpointUrl(SmarthubImpExt smarthubImpExt) {
        return endpointTemplate.replace("{{HOST}}", smarthubImpExt.getPartnerName())
                .replace("{{AccountID}}", smarthubImpExt.getSeat())
                .replace("{{SourceId}}", smarthubImpExt.getToken());
    }

    private SmarthubImpExt parseAndValidateImpExt(Imp imp) {
        final SmarthubImpExt smarthubImpExt;
        try {
            smarthubImpExt = mapper.mapper().convertValue(imp.getExt(), SMARTHUB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
        if (StringUtils.isBlank(smarthubImpExt.getPartnerName())) {
            throw new PreBidException("partnerName parameter is required for smarthub bidder");
        }
        if (StringUtils.isBlank(smarthubImpExt.getSeat())) {
            throw new PreBidException("seat parameter is required for smarthub bidder");
        }
        if (StringUtils.isBlank(smarthubImpExt.getToken())) {
            throw new PreBidException("token parameter is required for smarthub bidder");
        }
        return smarthubImpExt;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }


}
