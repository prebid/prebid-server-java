package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.model.IntentiqIdentityModuleContext;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.IiqParam;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs at the {@code auction-response} stage and reports each winning bid to the IntentIQ S2S
 * impression-reporting API (fire-and-forget GET to {@code reports-endpoint}). The {@code abTestUuid}
 * produced by the resolution hook is read from the module context. The bid response is never modified.
 */
public class IntentiqIdentityAuctionResponseHook implements AuctionResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(IntentiqIdentityAuctionResponseHook.class);

    public static final String CODE = "intentiq-identity-auction-response-hook";

    private static final long DEFAULT_TIMEOUT_MS = 1000L;
    private static final String BIDDING_PLATFORM_OPENRTB = "4";
    private static final String DEFAULT_CURRENCY = "USD";

    // Identifies the request source to the IntentIQ S2S API as prebid-server-java.
    private static final String SOURCE_PBJV = "pbjv";

    private final ConfigResolver configResolver;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;
    private final IntentiqIdentityMetrics metrics;

    public IntentiqIdentityAuctionResponseHook(ConfigResolver configResolver,
                                               HttpClient httpClient,
                                               JacksonMapper mapper,
                                               IntentiqIdentityMetrics metrics) {
        this.configResolver = Objects.requireNonNull(configResolver);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(AuctionResponsePayload auctionResponsePayload,
                                                                 AuctionInvocationContext invocationContext) {
        final IntentiqIdentityProperties properties = configResolver.resolve(invocationContext.accountConfig());
        final IntentiqIdentityModuleContext context =
                invocationContext.moduleContext() instanceof IntentiqIdentityModuleContext ctx ? ctx : null;
        // Whole-flow latency: enrich hook entry (startNanos) -> here (bid release), recorded once
        // per auction regardless of whether an impression report is configured/sent.
        if (context != null) {
            metrics.flowLatency(System.nanoTime() - context.startNanos(), properties.getPartnerId());
        }

        final BidResponse bidResponse = auctionResponsePayload.bidResponse();
        if (StringUtils.isBlank(properties.getReportsEndpoint()) || bidResponse == null) {
            return Future.succeededFuture(noAction());
        }

        final BidRequest bidRequest = bidRequest(invocationContext);
        final String abTestUuid = context != null ? context.abTestUuid() : null;
        final Long terminationCause = context != null ? context.terminationCause() : null;
        final String currency = bidResponse.getCur() != null ? bidResponse.getCur() : DEFAULT_CURRENCY;

        for (final SeatBid seatBid : nullSafe(bidResponse.getSeatbid())) {
            for (final Bid bid : nullSafe(seatBid.getBid())) {
                report(properties, bidRequest, seatBid.getSeat(), bid, currency, abTestUuid, terminationCause);
            }
        }

        return Future.succeededFuture(noAction());
    }

    private void report(IntentiqIdentityProperties properties,
                        BidRequest bidRequest,
                        String bidderCode,
                        Bid bid,
                        String currency,
                        String abTestUuid,
                        Long terminationCause) {
        final Map<String, Object> rdata = new LinkedHashMap<>();
        put(rdata, RdataField.BIDDER_CODE, bidderCode);
        put(rdata, RdataField.PARTNER_ID, properties.getPartnerId());
        put(rdata, RdataField.CPM, bid.getPrice());
        put(rdata, RdataField.CURRENCY, currency);
        appendOriginalBid(rdata, bid);
        put(rdata, RdataField.PLACEMENT_ID, bid.getImpid());
        put(rdata, RdataField.BIDDING_PLATFORM_ID, BIDDING_PLATFORM_OPENRTB);
        putIfPresent(rdata, RdataField.VRREF, resolveRef(bidRequest));
        final String auctionId = bidRequest != null ? bidRequest.getId() : null;
        putIfPresent(rdata, RdataField.PREBID_AUCTION_ID, auctionId);
        putIfPresent(rdata, RdataField.PARTNER_AUCTION_ID, auctionId);
        putIfPresent(rdata, RdataField.AB_TEST_UUID, abTestUuid);
        if (terminationCause != null) {
            put(rdata, RdataField.TERMINATION_CAUSE, terminationCause);
        }
        final Device device = bidRequest != null ? bidRequest.getDevice() : null;
        if (device != null) {
            putIfPresent(rdata, RdataField.IP,
                    StringUtils.isNotBlank(device.getIp()) ? device.getIp() : device.getIpv6());
            putIfPresent(rdata, RdataField.UA, device.getUa());
        }

        final String dpi = properties.getPartnerId();

        final long timeout = properties.getTimeout() != null ? properties.getTimeout() : DEFAULT_TIMEOUT_MS;
        httpClient.get(reportUrl(properties, rdata), HttpUtil.headers(), timeout)
                .onSuccess(response -> metrics.impressionReported(dpi))
                .onFailure(throwable -> {
                    metrics.impressionError(dpi);
                    logger.warn("IntentIQ impression report failed", throwable);
                });
    }

    private String reportUrl(IntentiqIdentityProperties properties, Map<String, Object> rdata) {
        final String endpoint = properties.getReportsEndpoint();
        return endpoint
                + (endpoint.contains("?") ? "&" : "?")
                + IiqParam.AT.key() + "=45"
                + "&" + IiqParam.RTYPE.key() + "=1"
                + "&" + IiqParam.SOURCE.key() + "=" + SOURCE_PBJV
                + "&" + IiqParam.DPI.key() + "=" + encodeComponent(StringUtils.defaultString(properties.getPartnerId()))
                + "&" + IiqParam.RDATA.key() + "=" + encodeComponent(mapper.encodeToString(rdata));
    }

    private static String resolveRef(BidRequest bidRequest) {
        if (bidRequest == null) {
            return null;
        }
        final Site site = bidRequest.getSite();
        if (site != null) {
            return StringUtils.isNotBlank(site.getDomain()) ? site.getDomain() : site.getPage();
        }
        final App app = bidRequest.getApp();
        if (app != null) {
            return StringUtils.isNotBlank(app.getBundle()) ? app.getBundle() : app.getName();
        }
        return null;
    }

    private static BidRequest bidRequest(AuctionInvocationContext invocationContext) {
        final AuctionContext auctionContext = invocationContext.auctionContext();
        return auctionContext != null ? auctionContext.getBidRequest() : null;
    }

    private static void appendOriginalBid(Map<String, Object> rdata, Bid bid) {
        final ObjectNode ext = bid.getExt();
        if (ext == null) {
            return;
        }
        final JsonNode originalCpm = ext.get("origbidcpm");
        if (originalCpm != null && originalCpm.isNumber()) {
            put(rdata, RdataField.ORIGINAL_CPM, originalCpm.decimalValue());
        }
        final JsonNode originalCurrency = ext.get("origbidcur");
        if (originalCurrency != null && StringUtils.isNotBlank(originalCurrency.asText())) {
            put(rdata, RdataField.ORIGINAL_CURRENCY, originalCurrency.asText());
        }
    }

    private static void put(Map<String, Object> map, RdataField field, Object value) {
        map.put(field.key(), value);
    }

    private static void putIfPresent(Map<String, Object> map, RdataField field, String value) {
        if (StringUtils.isNotBlank(value)) {
            map.put(field.key(), value);
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static String encodeComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static InvocationResult<AuctionResponsePayload> noAction() {
        return InvocationResultImpl.<AuctionResponsePayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .build();
    }

    @Override
    public String code() {
        return CODE;
    }

    /** Field names of the impression-report {@code rdata} JSON object sent to the IntentIQ S2S API. */
    private enum RdataField {

        BIDDER_CODE("bidderCode"),
        PARTNER_ID("partnerId"),
        CPM("cpm"),
        CURRENCY("currency"),
        ORIGINAL_CPM("originalCpm"),
        ORIGINAL_CURRENCY("originalCurrency"),
        PLACEMENT_ID("placementId"),
        BIDDING_PLATFORM_ID("biddingPlatformId"),
        VRREF("vrref"),
        PREBID_AUCTION_ID("prebidAuctionId"),
        PARTNER_AUCTION_ID("partnerAuctionId"),
        AB_TEST_UUID("abTestUuid"),
        TERMINATION_CAUSE("terminationCause"),
        IP("ip"),
        UA("ua");

        private final String key;

        RdataField(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }
}
