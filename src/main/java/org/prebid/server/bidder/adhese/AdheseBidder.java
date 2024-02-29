package org.prebid.server.bidder.adhese;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adhese.model.AdheseOriginData;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adhese.ExtImpAdhese;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public class AdheseBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdhese>> ADHESE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String SLOT_PARAMETER = "SL";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdheseBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        if (CollectionUtils.isEmpty(request.getImp())) {
            return Result.withError(BidderError.badInput("No impression in the bid request"));
        }

        final ExtImpAdhese extImpAdhese;
        try {
            extImpAdhese = parseImpExt(request.getImp().get(0));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String uri = getUrl(extImpAdhese);
        final BidRequest modifiedBidRequest = modifyBidRequest(request, extImpAdhese);

        return Result.withValue(BidderUtil.defaultRequest(modifiedBidRequest, uri, mapper));
    }

    private ExtImpAdhese parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADHESE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private String getUrl(ExtImpAdhese extImpAdhese) {
        return endpointUrl.replace("{{AccountId}}", extImpAdhese.getAccount());
    }

    private BidRequest modifyBidRequest(BidRequest bidRequest, ExtImpAdhese extImpAdhese) {
        final Map<String, List<String>> parameterMap = new TreeMap<>();
        parameterMap.putAll(getTargetParameters(extImpAdhese));
        parameterMap.putAll(getSlotParameter(extImpAdhese));

        final ObjectNode adheseExtInnerNode = mapper.mapper().valueToTree(parameterMap);
        final ObjectNode adheseExtNode = mapper.mapper().createObjectNode().set("adhese", adheseExtInnerNode);

        final Imp imp = bidRequest.getImp().get(0).toBuilder()
                .ext(adheseExtNode)
                .build();

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();
    }

    private Map<String, List<String>> getTargetParameters(ExtImpAdhese extImpAdhese) {
        final JsonNode targets = extImpAdhese.getTargets();
        return targets == null || targets.isEmpty() ? Collections.emptyMap() : parseTargetParametersAndSort(targets);
    }

    private Map<String, List<String>> parseTargetParametersAndSort(JsonNode targets) {
        return new TreeMap<>(
                mapper.mapper().convertValue(targets, new TypeReference<Map<String, List<String>>>() {
                }));
    }

    private static Map<String, List<String>> getSlotParameter(ExtImpAdhese extImpAdhese) {
        final String slot = "%s-%s".formatted(
                HttpUtil.encodeUrl(extImpAdhese.getLocation()),
                HttpUtil.encodeUrl(extImpAdhese.getFormat()));
        return Collections.singletonMap(SLOT_PARAMETER, Collections.singletonList(slot));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final Optional<Bid> optionalBid = getBid(bidResponse);

            if (optionalBid.isEmpty()) {
                return Result.empty();
            }

            final Bid bid = optionalBid.get();
            final AdheseOriginData originData = toObjectOfType(bid.getExt().get("adhese"), AdheseOriginData.class);
            final Bid modifiedBid = bid.toBuilder()
                    .ext(mapper.mapper().valueToTree(originData)) // unwrap from "adhese"
                    .build();

            final BidderBid bidderBid = BidderBid.of(modifiedBid, getBidType(bidRequest), bidResponse.getCur());

            return Result.of(Collections.singletonList(bidderBid), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static Optional<Bid> getBid(BidResponse bidResponse) {
        return Optional.ofNullable(bidResponse)
                .map(BidResponse::getSeatbid)
                .stream()
                .flatMap(Collection::stream)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .findFirst();
    }

    private <T> T toObjectOfType(JsonNode jsonNode, Class<T> clazz) {
        try {
            return mapper.mapper().treeToValue(jsonNode, clazz);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BidType getBidType(BidRequest bidRequest) {
        final List<Imp> impList = bidRequest.getImp();

        if (impList == null || impList.isEmpty()) {
            throw new PreBidException("No Imps available");
        }

        final Imp firstImp = impList.get(0);
        if (firstImp.getBanner() != null) {
            return BidType.banner;
        } else if (firstImp.getVideo() != null) {
            return BidType.video;
        } else if (firstImp.getXNative() != null) {
            return BidType.xNative;
        } else if (firstImp.getAudio() != null) {
            return BidType.audio;
        } else {
            throw new PreBidException("Failed to obtain BidType");
        }
    }
}
