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
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adhese.model.AdheseBid;
import org.prebid.server.bidder.adhese.model.AdheseOriginData;
import org.prebid.server.bidder.adhese.model.AdheseRequestBody;
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

public class AdheseBidder implements Bidder<AdheseRequestBody> {

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
    public Result<List<HttpRequest<AdheseRequestBody>>> makeHttpRequests(BidRequest request) {
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
        final AdheseRequestBody body = buildBody(request, extImpAdhese);

        return Result.of(Collections.singletonList(
                        HttpRequest.<AdheseRequestBody>builder()
                                .method(HttpMethod.POST)
                                .uri(uri)
                                .body(mapper.encodeToBytes(body))
                                .payload(body)
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
        return headers;
    }

    private ExtImpAdhese parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADHESE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private AdheseRequestBody buildBody(BidRequest request, ExtImpAdhese extImpAdhese) {
        final Map<String, List<String>> parameterMap = new TreeMap<>();
        parameterMap.putAll(getTargetParameters(extImpAdhese));
        parameterMap.putAll(getGdprParameter(request.getUser()));
        parameterMap.putAll(getRefererParameter(request.getSite()));
        parameterMap.putAll(getIfaParameter(request.getDevice()));

        return AdheseRequestBody.builder()
                .slots(Collections.singletonList(
                        AdheseRequestBody.Slot.builder()
                                .slotname(getSlotParameter(extImpAdhese))
                                .build()))
                .parameters(parameterMap)
                .build();
    }

    private String getUrl(ExtImpAdhese extImpAdhese) {
        return endpointUrl.replace("{{AccountId}}", extImpAdhese.getAccount());
    }

    private static String getSlotParameter(ExtImpAdhese extImpAdhese) {
        return String.format("%s-%s",
                HttpUtil.encodeUrl(extImpAdhese.getLocation()),
                HttpUtil.encodeUrl(extImpAdhese.getFormat()));
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

    private static Map<String, List<String>> getGdprParameter(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        return StringUtils.isNotBlank(consent)
                ? Collections.singletonMap(GDPR_QUERY_PARAMETER, Collections.singletonList(consent))
                : Collections.emptyMap();
    }

    private static Map<String, List<String>> getRefererParameter(Site site) {
        final String page = site != null ? site.getPage() : null;
        return StringUtils.isNotBlank(page)
                ? Collections.singletonMap(REFERER_QUERY_PARAMETER, Collections.singletonList(page))
                : Collections.emptyMap();
    }

    private static Map<String, List<String>> getIfaParameter(Device device) {
        final String ifa = device != null ? device.getIfa() : null;
        return StringUtils.isNotBlank(ifa)
                ? Collections.singletonMap(IFA_QUERY_PARAMETER, Collections.singletonList(ifa))
                : Collections.emptyMap();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<AdheseRequestBody> httpCall, BidRequest bidRequest) {
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
