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
import org.prebid.server.bidder.adhese.model.Cpm;
import org.prebid.server.bidder.adhese.model.CpmValues;
import org.prebid.server.bidder.adhese.model.Prebid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Adhese {@link Bidder} implementation.
 */
public class AdheseBidder implements Bidder<Void> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdhese>> ADHESE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdhese>>() {
            };

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

        final ExtImpAdhese extImpAdhese;
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
                        .headers(HttpUtil.headers())
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
        final JsonNode keywords = extImpAdhese.getKeywords();
        if (keywords == null || keywords.isNull()) {
            return "";
        }

        final Map<String, List<String>> targetParameters = parseTargetParametersAndSort(keywords);
        return targetParameters.entrySet().stream()
                .map(entry -> createPartOrUrl(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining());
    }

    private Map<String, List<String>> parseTargetParametersAndSort(JsonNode keywords) {
        return new TreeMap<>(
                mapper.mapper().convertValue(keywords, new TypeReference<Map<String, List<String>>>() {
                }));
    }

    private static String createPartOrUrl(String key, List<String> values) {
        final String formattedValues = String.join(";", values);
        return String.format("/%s%s", HttpUtil.encodeUrl(key), formattedValues);
    }

    private static String getGdprParameter(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        return StringUtils.isNotBlank(consent)
                ? String.format("%s%s", QUERY_PARAMETER_GDPR, consent)
                : "";
    }

    private static String getRefererParameter(Site site) {
        final String page = site != null ? site.getPage() : null;
        return StringUtils.isNotBlank(page)
                ? String.format("%s%s", QUERY_PARAMETER_REFERER, HttpUtil.encodeUrl(page))
                : "";
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final HttpResponse httpResponse = httpCall.getResponse();

        final int statusCode = httpResponse.getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.empty();
        }

        final JsonNode bodyNode;
        try {
            bodyNode = mapper.decodeValue(httpResponse.getBody(), JsonNode.class);
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
        if (!bodyNode.isArray()) {
            return Result.emptyWithError(BidderError.badServerResponse("Unexpected response body"));
        }
        if (bodyNode.size() == 0) {
            return Result.empty();
        }

        final JsonNode bidNode = bodyNode.get(0);
        final AdheseBid adheseBid;
        try {
            adheseBid = toObjectOfType(bidNode, AdheseBid.class);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        final Bid bid;
        if (Objects.equals(adheseBid.getOrigin(), ORIGIN)) {
            final AdheseResponseExt responseExt;
            final AdheseOriginData originData;
            try {
                responseExt = toObjectOfType(bidNode, AdheseResponseExt.class);
                originData = toObjectOfType(bidNode, AdheseOriginData.class);
            } catch (PreBidException e) {
                return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
            }
            bid = convertAdheseBid(adheseBid, responseExt, originData);
        } else {
            bid = convertAdheseOpenRtbBid(adheseBid);
        }
        if (bid == null) {
            return Result.emptyWithError(BidderError.badServerResponse("Response resulted in an empty seatBid array"));
        }

        final BigDecimal price;
        final Integer width;
        final Integer height;
        try {
            price = getPrice(adheseBid);
            width = Integer.valueOf(adheseBid.getWidth());
            height = Integer.valueOf(adheseBid.getHeight());
        } catch (NumberFormatException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        final Bid updatedBid = Bid.builder()
                .id("1") // hardcoded because it is not provided and should be not empty value
                .impid(bidRequest.getImp().get(0).getId())
                .price(price)
                .w(width)
                .h(height)
                .dealid(bid.getDealid())
                .crid(bid.getCrid())
                .adm(bid.getAdm())
                .ext(bid.getExt())
                .build();

        final BidderBid bidderBid = BidderBid.of(updatedBid, getBidType(bid.getAdm()), getCurrency(adheseBid));
        return Result.of(Collections.singletonList(bidderBid), Collections.emptyList());
    }

    private <T> T toObjectOfType(JsonNode jsonNode, Class<T> clazz) {
        try {
            return mapper.mapper().treeToValue(jsonNode, clazz);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Bid convertAdheseBid(AdheseBid adheseBid, AdheseResponseExt adheseResponseExt,
                                 AdheseOriginData adheseOriginData) {
        return Bid.builder()
                .dealid(adheseResponseExt.getOrderId())
                .crid(adheseResponseExt.getId())
                .adm(getAdMarkup(adheseBid, adheseResponseExt))
                .ext(mapper.mapper().valueToTree(adheseOriginData))
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
        final BidResponse originData = adheseBid.getOriginData();
        final List<SeatBid> seatBids = originData != null ? originData.getSeatbid() : null;

        return CollectionUtils.emptyIfNull(seatBids).stream()
                .filter(Objects::nonNull)
                .flatMap(seatBid -> seatBid.getBid().stream())
                .filter(Objects::nonNull)
                .findFirst()
                .map(bid -> bid.toBuilder().adm(adheseBid.getBody()).build())
                .orElse(null);
    }

    private static BigDecimal getPrice(AdheseBid adheseBid) {
        final CpmValues cpmValues = getCpmValues(adheseBid.getExtension());
        final String amount = cpmValues != null ? cpmValues.getAmount() : null;
        return new BigDecimal(StringUtils.stripToEmpty(amount));
    }

    private static String getCurrency(AdheseBid adheseBid) {
        final CpmValues cpmValues = getCpmValues(adheseBid.getExtension());
        return cpmValues != null ? StringUtils.stripToNull(cpmValues.getCurrency()) : null;
    }

    private static CpmValues getCpmValues(Prebid prebid) {
        final Cpm cpm = prebid != null ? prebid.getPrebid() : null;
        return cpm != null ? cpm.getCpm() : null;
    }

    private static BidType getBidType(String bidAdm) {
        return StringUtils.containsAny(bidAdm, "<?xml", "<vast")
                ? BidType.video
                : BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
