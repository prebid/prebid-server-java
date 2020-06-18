package org.prebid.server.bidder.adhese;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adhese.model.AdheseBid;
import org.prebid.server.bidder.adhese.model.AdheseOriginData;
import org.prebid.server.bidder.adhese.model.AdheseResponseExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adhese.ExtImpAdhese;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class AdheseBidder implements Bidder<Void> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdhese>> ADHESE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdhese>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdheseBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        if (CollectionUtils.isEmpty(request.getImp())) {
            return Result.emptyWithError(BidderError.badInput("No impression in the bid request"));
        }

        ExtImpAdhese extImpAdhese;
        try {
            extImpAdhese = parseImpExt(request.getImp().get(0));
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }

        final String uri = buildUrl(request, endpointUrl, extImpAdhese);

        return Result.of(Collections.singletonList(
                HttpRequest.<Void>builder()
                        .method(HttpMethod.GET)
                        .uri(uri)
                        .body(null)
                        .headers(HttpUtil.headers())
                        .payload(null)
                        .build()),
                Collections.emptyList());
    }

    private ExtImpAdhese parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADHESE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private String buildUrl(BidRequest request, String endpointUrl, ExtImpAdhese extImpAdhese) {
        final String uri = endpointUrl.replace("{{AccountId}}", extImpAdhese.getAccount());
        final String slotParameter = String.format("/sl%s-%s", HttpUtil.encodeUrl(extImpAdhese.getLocation()),
                HttpUtil.encodeUrl(extImpAdhese.getFormat()));

        return String.format("%s%s%s%s%s", uri, slotParameter, getTargetParameters(extImpAdhese),
                getGdprParameter(request), getRefererParameter(request));
    }

    private String getTargetParameters(ExtImpAdhese extImpAdhese) {
        if (extImpAdhese.getKeywords().isNull()) {
            return "";
        }
        StringBuilder parametersAsString = new StringBuilder();
        final Map<String, List<String>> targetParameters = parseTargetParametersAndSort(extImpAdhese.getKeywords());

        for (Map.Entry<String, List<String>> targetParametersMap : targetParameters.entrySet()) {
            parametersAsString.append("/").append(HttpUtil.encodeUrl(targetParametersMap.getKey()));
            for (String parameter : targetParametersMap.getValue()) {
                parametersAsString.append(parameter).append(";");
            }
            parametersAsString.deleteCharAt(parametersAsString.lastIndexOf(";"));
        }

        return parametersAsString.toString();
    }

    private Map<String, List<String>> parseTargetParametersAndSort(JsonNode keywords) {
        Map<String, List<String>> sortedMap = new TreeMap<>();
        if (keywords != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = keywords.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> next = fields.next();
                List<String> values = null;
                if (next.getValue() instanceof ArrayNode) {
                    final ArrayNode arrayNode = (ArrayNode) next.getValue();
                    values = StreamSupport.stream(arrayNode.spliterator(), false)
                            .map(JsonNode::toString)
                            .map(m -> m.substring(1, m.length() - 1))
                            .collect(Collectors.toList());
                }
                sortedMap.put(next.getKey(), values);
            }
        }
        return sortedMap;
    }

    private String getGdprParameter(BidRequest request) {
        if (request.getUser() != null && StringUtils.isNotBlank(extUser(request.getUser().getExt()).getConsent())) {
            return "/xt" + extUser(request.getUser().getExt()).getConsent();
        }
        return "";
    }

    private ExtUser extUser(ObjectNode extNode) {
        try {
            return extNode != null ? mapper.mapper().treeToValue(extNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private String getRefererParameter(BidRequest request) {
        if (request.getSite() != null && StringUtils.isNotBlank(request.getSite().getPage())) {
            return "/xf" + HttpUtil.encodeUrl(request.getSite().getPage());
        }
        return "";
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        final AdheseBid adheseBid;
        try {
            adheseBid = decodeBodyToBid(httpCall);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        BidResponse.BidResponseBuilder bidResponseBuilder;
        if (adheseBid.getOrigin().equals("JERLICIA")) {
            final AdheseResponseExt adheseResponseExt;
            final AdheseOriginData adheseOriginData;
            try {
                adheseResponseExt = decodeBodyToBidResponse(httpCall);
                adheseOriginData = decodeBodyToData(httpCall);
            } catch (PreBidException e) {
                return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
            }
            bidResponseBuilder = convertAdheseBid(adheseBid, adheseResponseExt, adheseOriginData);
        } else {
            bidResponseBuilder = convertAdheseOpenRtbBid(adheseBid);
        }

        final BigDecimal price = new BigDecimal(adheseBid.getExtension().getPrebid().getCpm().getAmount());
        final Integer width = Integer.valueOf(adheseBid.getWidth());
        final Integer height = Integer.valueOf(adheseBid.getHeight());

        final BidResponse bidResponse = bidResponseBuilder
                .cur(adheseBid.getExtension().getPrebid().getCpm().getCurrency())
                .build();

        if (CollectionUtils.isNotEmpty(bidResponse.getSeatbid())
                && CollectionUtils.isNotEmpty(bidResponse.getSeatbid().get(0).getBid())) {
            bidResponseBuilder.seatbid(Collections.singletonList(
                    SeatBid.builder()
                            .bid(Collections.singletonList(Bid.builder()
                                    .price(price)
                                    .w(width)
                                    .h(height)
                                    .dealid(bidResponse.getSeatbid().get(0).getBid().get(0).getDealid())
                                    .crid(bidResponse.getSeatbid().get(0).getBid().get(0).getCrid())
                                    .adm(bidResponse.getSeatbid().get(0).getBid().get(0).getAdm())
                                    .ext(bidResponse.getSeatbid().get(0).getBid().get(0).getExt())
                                    .build()))
                            .build()))
                    .cur(adheseBid.getExtension().getPrebid().getCpm().getCurrency())
                    .build();
        }

        final BidResponse updatedBidResponse = bidResponseBuilder.build();

        if (updatedBidResponse != null && CollectionUtils.isEmpty(updatedBidResponse.getSeatbid())) {
            return Result.emptyWithError(BidderError
                    .badServerResponse("Response resulted in an empty seatBid array. %s."));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = updatedBidResponse != null ? updatedBidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidFromResponse(bid, bidRequest.getImp().get(0).getId(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()) : null;
        return Result.of(bidderBids, errors);
    }

    private AdheseBid decodeBodyToBid(HttpCall<Void> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), AdheseBid.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private AdheseResponseExt decodeBodyToBidResponse(HttpCall<Void> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), AdheseResponseExt.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private AdheseOriginData decodeBodyToData(HttpCall<Void> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), AdheseOriginData.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private BidResponse.BidResponseBuilder convertAdheseBid(AdheseBid adheseBid, AdheseResponseExt adheseResponseExt,
                                                            AdheseOriginData adheseOriginData) {
        final ObjectNode adheseExtJson = mapper.mapper().valueToTree(adheseOriginData);

        return BidResponse.builder()
                .id(adheseResponseExt.getId())
                .seatbid(Collections.singletonList(
                        SeatBid.builder()
                                .bid(Collections.singletonList(Bid.builder()
                                        .id("1")
                                        .dealid(adheseResponseExt.getOrderId())
                                        .crid(adheseResponseExt.getId())
                                        .adm(getAdMarkup(adheseBid, adheseResponseExt))
                                        .ext(adheseExtJson)
                                        .build()))
                                .build()));
    }

    private String getAdMarkup(AdheseBid adheseBid, AdheseResponseExt adheseResponseExt) {
        if (adheseResponseExt.getExt().equals("js")) {
            if (StringUtils.containsAny(adheseBid.getBody(), "<script", "<div", "<html")) {
                String counter = "";
                if (StringUtils.isNotBlank(adheseResponseExt.getImpressionCounter())) {
                    counter = "<img src='" + adheseResponseExt.getImpressionCounter()
                            + "' style='height:1px; width:1px; margin: -1px -1px; display:none;'/>";
                }
                return adheseBid.getBody() + counter;
            }
            if (StringUtils.containsAny(adheseBid.getBody(), "<?xml", "<vast")) {
                return adheseBid.getBody();
            }
        }
        return adheseResponseExt.getTag();
    }

    private BidResponse.BidResponseBuilder convertAdheseOpenRtbBid(AdheseBid adheseBid) {
        BidResponse.BidResponseBuilder bidResponseBuilder = adheseBid.getOriginData().builder();
        if (CollectionUtils.isNotEmpty(adheseBid.getOriginData().getSeatbid())
                && CollectionUtils.isNotEmpty(adheseBid.getOriginData().getSeatbid().get(0).getBid())) {
            bidResponseBuilder = bidResponseBuilder
                    .seatbid(Collections.singletonList(
                            SeatBid.builder()
                                    .bid(Collections.singletonList(Bid.builder().adm(adheseBid.getBody()).build()))
                                    .build()));
        }
        return bidResponseBuilder;
    }

    private static BidderBid bidFromResponse(Bid bid, String impId, List<BidderError> errors) {
        /**
         * It is setted bidId =1, because it is not provided by vendor and should be not empty value
         */
        try {
            final BidType bidType = getBidType(bid.getAdm());
            final Bid updateBid = bid.toBuilder().id("1").impid(impId).build();
            return BidderBid.of(updateBid, bidType, DEFAULT_BID_CURRENCY);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(String bidAdm) {
        if (StringUtils.isNotBlank(bidAdm) && StringUtils.containsAny(bidAdm, "<?xml", "<vast")) {
            return BidType.video;
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
