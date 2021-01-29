package org.prebid.server.bidder.adhese;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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

    private static final String ORIGIN_BID = "JERLICIA";
    private static final String GDPR_QUERY_PARAMETER = "xt";
    private static final String REFERER_QUERY_PARAMETER = "xf";
    private static final String IFA_QUERY_PARAMETER = "xz";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdheseBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
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


        return Result.of(Collections.singletonList(
                HttpRequest.<Void>builder()
                        .method(HttpMethod.POST)
                        .uri(uri)
                        .body(buildBody(request, extImpAdhese))
                        .headers(replaceHeaders(request.getDevice()))
                        .build()),
                Collections.emptyList());
    }

    private MultiMap replaceHeaders(Device device) {
        MultiMap headers = HttpUtil.headers();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpHeaders.createOptimized("X-Real-IP"), device.getIp());
        }
        return HttpUtil.headers();
    }

    private ExtImpAdhese parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADHESE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private String buildBody(BidRequest request, ExtImpAdhese extImpAdhese) {
        JsonObject main = new JsonObject();

        JsonArray slotsArray = new JsonArray();
        slotsArray.add(getSlotParameter(extImpAdhese));
        main.put("slots", slotsArray);

        JsonObject parameters = new JsonObject();
        insertTargetParameters(extImpAdhese, parameters);
        insertGdprParameter(request.getUser(), parameters);
        insertRefererParameter(request.getSite(), parameters);
        insertIfaParameter(request.getDevice(), parameters);
        main.put("parameters", parameters);

        return main.toString();
    }

    private String getUrl(ExtImpAdhese extImpAdhese) {
        return endpointUrl.replace("{{AccountId}}", extImpAdhese.getAccount());
    }

    private static JsonObject getSlotParameter(ExtImpAdhese extImpAdhese) {
        JsonObject slots = new JsonObject();
        slots.put("slotname", String.format("%s-%s",
                HttpUtil.encodeUrl(extImpAdhese.getLocation()),
                HttpUtil.encodeUrl(extImpAdhese.getFormat())));
        return slots;
    }

    private void insertTargetParameters(ExtImpAdhese extImpAdhese, JsonObject parameters) {
        final JsonNode targets = extImpAdhese.getTargets();
        if (!(targets == null || targets.isNull())) {
            final Map<String, List<String>> targetParameters = parseTargetParametersAndSort(targets);
            targetParameters.forEach((k, v) -> parameters.put(k, new JsonArray(v)));
        }
    }

    private Map<String, List<String>> parseTargetParametersAndSort(JsonNode targets) {
        return new TreeMap<>(
                mapper.mapper().convertValue(targets, new TypeReference<Map<String, List<String>>>() {
                }));
    }

    private static void insertGdprParameter(User user, JsonObject parameters) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        if (StringUtils.isNotBlank(consent)) {
            parameters.put(GDPR_QUERY_PARAMETER, new JsonArray(Collections.singletonList(consent)));
        }
    }

    private static void insertRefererParameter(Site site, JsonObject parameters) {
        final String page = site != null ? site.getPage() : null;
        if (StringUtils.isNotBlank(page)) {
            parameters.put(REFERER_QUERY_PARAMETER, new JsonArray(Collections.singletonList(page)));
        }
    }

    private static void insertIfaParameter(Device device, JsonObject parameters) {
        final String ifa = device != null ? device.getIfa() : null;
        if (StringUtils.isNotBlank(ifa)) {
            parameters.put(IFA_QUERY_PARAMETER, new JsonArray(Collections.singletonList(ifa)));
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final HttpResponse httpResponse = httpCall.getResponse();

        final JsonNode bodyNode;
        try {
            bodyNode = mapper.decodeValue(httpResponse.getBody(), JsonNode.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
        if (!bodyNode.isArray()) {
            return Result.withError(BidderError.badServerResponse("Unexpected response body"));
        }
        if (bodyNode.size() == 0) {
            return Result.empty();
        }

        final JsonNode bidNode = bodyNode.get(0);
        final AdheseBid adheseBid;
        try {
            adheseBid = toObjectOfType(bidNode, AdheseBid.class);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final Bid bid;
        if (Objects.equals(adheseBid.getOrigin(), ORIGIN_BID)) {
            final AdheseResponseExt responseExt;
            final AdheseOriginData originData;
            try {
                responseExt = toObjectOfType(bidNode, AdheseResponseExt.class);
                originData = toObjectOfType(bidNode, AdheseOriginData.class);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badServerResponse(e.getMessage()));
            }
            bid = convertAdheseBid(adheseBid, responseExt, originData);
        } else {
            bid = convertAdheseOpenRtbBid(adheseBid);
        }
        if (bid == null) {
            return Result.withError(BidderError.badServerResponse("Response resulted in an empty seatBid array"));
        }

        final BigDecimal price;
        final Integer width;
        final Integer height;
        try {
            price = getPrice(adheseBid);
            width = Integer.valueOf(adheseBid.getWidth());
            height = Integer.valueOf(adheseBid.getHeight());
        } catch (NumberFormatException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
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
}
