package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmaticKeyVal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pubmatic {@link Bidder} implementation.
 */
public class PubmaticBidder implements Bidder<BidRequest> {

    private static final Logger logger = LoggerFactory.getLogger(PubmaticBidder.class);

    private static final TypeReference<ExtPrebid<?, ExtImpPubmatic>> PUBMATIC_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpPubmatic>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public PubmaticBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();

        String pubId = null;
        String wrapExt = null;
        final List<Imp> parsedImps = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpPubmatic extImpPubmatic = parsePubmaticExt(imp);
                final Imp parsedImp = parseImpObject(imp, extImpPubmatic);

                if (pubId == null) {
                    pubId = extImpPubmatic.getPublisherId();
                }
                if (wrapExt == null && extImpPubmatic.getWrapExt() != null) {
                    wrapExt = getWrapExt(imp, extImpPubmatic);
                }
                parsedImps.add(parsedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (parsedImps.size() == 0) {
            return Result.of(Collections.emptyList(), errors);
        }

        requestBuilder.imp(parsedImps);

        if (wrapExt != null) {
            final String stringExt = String.format("{\"wrapper\": %s}", wrapExt);
            try {
                final ObjectNode modifiedExt = Json.mapper.readValue(stringExt, ObjectNode.class);
                requestBuilder.ext(modifiedExt);
            } catch (IOException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        modifySiteOrApp(pubId, bidRequest, requestBuilder);

        final BidRequest outgoingRequest = requestBuilder.build();
        final String body = Json.encode(outgoingRequest);

        return Result.of(
                Collections.singletonList(HttpRequest.of(HttpMethod.POST, endpointUrl, body, BidderUtil.headers(),
                        outgoingRequest)), errors);
    }

    private void modifySiteOrApp(String pubId,
                                 BidRequest bidRequest,
                                 BidRequest.BidRequestBuilder bidRequestBuilder) {
        if (bidRequest.getSite() != null) {
            final Site site = bidRequest.getSite();
            if (site.getPublisher() != null) {
                final Publisher modifiedPublisher = site.getPublisher().toBuilder().id(pubId).build();
                bidRequestBuilder.site(site.toBuilder().publisher(modifiedPublisher).build());
            } else {
                bidRequestBuilder.site(site.toBuilder()
                        .publisher(Publisher.builder().id(pubId).build())
                        .build());
            }
        } else if (bidRequest.getApp() != null) {
            final App app = bidRequest.getApp();
            if (app.getPublisher() != null) {
                final Publisher modifiedPublisher = app.getPublisher().toBuilder().id(pubId).build();
                bidRequestBuilder.app(app.toBuilder().publisher(modifiedPublisher).build());
            } else {
                bidRequestBuilder.app(app.toBuilder()
                        .publisher(Publisher.builder().id(pubId).build())
                        .build());
            }
        }
    }

    private String getWrapExt(Imp imp, ExtImpPubmatic extImpPubmatic) {
        try {
            Json.mapper.convertValue(extImpPubmatic.getWrapExt(), new TypeReference<Map<String, Integer>>() {
            });
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Error in Wrapper Parameters = %s  for ImpID = %s WrapperExt = %s",
                    e.getMessage(), imp.getId(), extImpPubmatic.getWrapExt().toString()));
        }
        return extImpPubmatic.getWrapExt().toString();
    }

    private Imp parseImpObject(Imp imp, ExtImpPubmatic extImpPubmatic) {
        final Imp.ImpBuilder result = imp.toBuilder();

        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format("Invalid MediaType. PubMatic only supports Banner and Video. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }
        if (imp.getAudio() != null) {
            result.audio(null);
        }

        final String adSlotString = extImpPubmatic.getAdSlot().trim();
        final String[] adSlot = adSlotString.split("@");

        if (imp.getBanner() != null) {
            if (adSlot.length == 2 && !adSlot[0].equals("") && !adSlot[1].equals("")) {
                result.tagid(adSlot[0]);

                final String[] adSize = adSlot[1].trim().toLowerCase().split("x");
                if (adSize.length == 2) {
                    Integer width;
                    Integer height;
                    final String[] heightStr = adSize[1].trim().split(":");
                    try {
                        width = Integer.valueOf(adSize[0]);
                        height = Integer.valueOf(heightStr[0]);
                    } catch (NumberFormatException e) {
                        throw new PreBidException("Invalid width or height provided in adSlot");
                    }
                    final Banner newBanner = imp.getBanner().toBuilder().w(width).h(height).build();
                    result.banner(newBanner);
                } else {
                    throw new PreBidException("Invalid size provided in adSlot");
                }
            } else {
                throw new PreBidException("Invalid adSlot provided");
            }
        }

        if (CollectionUtils.isNotEmpty(extImpPubmatic.getKeywords())) {
            final String keywordsString = makeKeywordString(extImpPubmatic.getKeywords());
            try {
                result.ext(Json.mapper.readValue(keywordsString, ObjectNode.class));
            } catch (IOException e) {
                throw new PreBidException(e.getMessage(), e);
            }
        } else {
            result.ext(null);
        }
        return result.build();
    }

    private String makeKeywordString(List<ExtImpPubmaticKeyVal> keywords) {
        List<String> eachKv = new ArrayList<>();
        for (ExtImpPubmaticKeyVal keyVal : keywords) {
            if (CollectionUtils.isEmpty(keyVal.getValues())) {
                logger.error(String.format("No values present for key = %s", keyVal.getKey()));
            } else {
                eachKv.add(String.format("\"%s\":\"%s\"", keyVal.getKey(),
                        String.join(",", keyVal.getValues())));
            }
        }
        return "{" + String.join(",", eachKv) + "}";
    }

    private ExtImpPubmatic parsePubmaticExt(Imp imp) {
        ExtImpPubmatic extImpPubmatic;
        try {
            extImpPubmatic = Json.mapper.<ExtPrebid<?, ExtImpPubmatic>>convertValue(imp.getExt(),
                    PUBMATIC_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
        return extImpPubmatic;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getMediaTypes(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private BidType getMediaTypes(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
