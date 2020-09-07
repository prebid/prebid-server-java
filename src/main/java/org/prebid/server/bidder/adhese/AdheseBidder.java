package org.prebid.server.bidder.adhese;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class AdheseBidder implements Bidder<Void> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdhese>> ADHESE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdhese>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String ORIGIN = "JERLICIA";
    private static final String QUERY_PARAMETER_GDPR = "/xt";
    private static final String QUERY_PARAMETER_REFERER = "/xf";

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
                getGdprParameter(request.getUser()), getRefererParameter(request.getSite()));
    }

    private String getTargetParameters(ExtImpAdhese extImpAdhese) {
        if (extImpAdhese.getKeywords().isNull()) {
            return "";
        }

        final Map<String, List<String>> targetParameters = parseTargetParametersAndSort(extImpAdhese.getKeywords());
        return targetParameters.entrySet().stream()
                .map(stringListEntry -> createPartOrUrl(stringListEntry.getKey(), stringListEntry.getValue()))
                .collect(Collectors.joining());
    }

    private Map<String, List<String>> parseTargetParametersAndSort(JsonNode keywords) {
        return keywords != null ? new TreeMap<>(
                mapper.mapper().convertValue(keywords, new TypeReference<Map<String, List<String>>>() {
                })) : null;
    }

    private String createPartOrUrl(String key, List<String> values) {
        final String formattedValues = String.join(";", values);
        return String.format("/%s%s", HttpUtil.encodeUrl(key), formattedValues);
    }

    private String getGdprParameter(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        return StringUtils.isNotBlank(consent) ? String.format("%s%s", QUERY_PARAMETER_GDPR, consent) : "";
    }

    private String getRefererParameter(Site site) {
        final String page = site != null ? site.getPage() : null;
        return StringUtils.isNotBlank(page)
                ? String.format("%s%s", QUERY_PARAMETER_REFERER, HttpUtil.encodeUrl(page))
                : "";
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

        final List<AdheseBid> adheseBid;
        final List<AdheseResponseExt> adheseResponseExt;
        final List<AdheseOriginData> adheseOriginData;
        final AdheseBid firstAdheseBid;
        Bid bid;
        try {
            adheseBid = decodeBodyToBidList(httpCall, AdheseBid.class);
            firstAdheseBid = adheseBid.get(0);
            if (Objects.equals(firstAdheseBid.getOrigin(), ORIGIN)) {
                adheseResponseExt = decodeBodyToBidList(httpCall, AdheseResponseExt.class);
                adheseOriginData = decodeBodyToBidList(httpCall, AdheseOriginData.class);
                bid = convertAdheseBid(firstAdheseBid, adheseResponseExt.get(0), adheseOriginData.get(0));
            } else {
                bid = convertAdheseOpenRtbBid(firstAdheseBid);
            }
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        final BigDecimal price = new BigDecimal(firstAdheseBid.getExtension().getPrebid().getCpm().getAmount());
        final Integer width = Integer.valueOf(firstAdheseBid.getWidth());
        final Integer height = Integer.valueOf(firstAdheseBid.getHeight());

        final Bid updateBid = bid != null
                ? Bid.builder()
                .price(price)
                .w(width)
                .h(height)
                .dealid(bid.getDealid())
                .crid(bid.getCrid())
                .adm(bid.getAdm())
                .ext(bid.getExt())
                .build()
                : null;

        if (updateBid == null) {
            return Result.emptyWithError(BidderError
                    .badServerResponse("Response resulted in an empty seatBid array. %s."));
        }

        /**
         * Used ImpId from Imp of bidRequest, because it is not provided and should be not empty value
         */
        final List<BidderError> errors = new ArrayList<>();
        return Result.of(Collections.singletonList(makeBid(updateBid, bidRequest.getImp().get(0).getId(), errors)),
                errors);
    }

    private <T> List<T> decodeBodyToBidList(HttpCall<Void> httpCall, Class<T> bidClassName) {
        try {
            return mapper.mapper().readValue(
                    httpCall.getResponse().getBody(),
                    mapper.mapper().getTypeFactory().constructCollectionType(List.class, bidClassName));
        } catch (DecodeException | JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Bid convertAdheseBid(AdheseBid adheseBid, AdheseResponseExt adheseResponseExt,
                                 AdheseOriginData adheseOriginData) {
        final ObjectNode adheseExtJson = mapper.mapper().valueToTree(adheseOriginData);

        return Bid.builder()
                .id("1")
                .dealid(adheseResponseExt.getOrderId())
                .crid(adheseResponseExt.getId())
                .adm(getAdMarkup(adheseBid, adheseResponseExt))
                .ext(adheseExtJson)
                .build();
    }

    private String getAdMarkup(AdheseBid adheseBid, AdheseResponseExt adheseResponseExt) {
        if (Objects.equals(adheseResponseExt.getExt(), "js")) {
            if (StringUtils.containsAny(adheseBid.getBody(), "<script", "<div", "<html")) {
                String counter = "";
                if (adheseResponseExt.getImpressionCounter().length() > 0) {
                    counter = String.format("%s%s%s", "<img src='", adheseResponseExt.getImpressionCounter(),
                            "' style='height:1px; width:1px; margin: -1px -1px; display:none;'/>");
                }
                return String.format("%s%s", adheseBid.getBody(), counter);
            }
            if (StringUtils.containsAny(adheseBid.getBody(), "<?xml", "<vast")) {
                return adheseBid.getBody();
            }
        }
        return adheseResponseExt.getTag();
    }

    private Bid convertAdheseOpenRtbBid(AdheseBid adheseBid) {
        final List<SeatBid> seatbid = adheseBid.getOriginData().getSeatbid();
        return CollectionUtils.isNotEmpty(seatbid)
                && CollectionUtils.isNotEmpty(seatbid.get(0).getBid())
                ? Bid.builder().adm(adheseBid.getBody()).build()
                : null;
    }

    private static BidderBid makeBid(Bid bid, String impId, List<BidderError> errors) {
        /**
         * Hardcoded bidId =1, because it is not provided and should be not empty value
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
        return (StringUtils.isNotBlank(bidAdm) && StringUtils.containsAny(bidAdm, "<?xml", "<vast"))
                ? BidType.video
                : BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
