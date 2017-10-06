package org.rtb.vexing.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.rtb.vexing.adapter.model.RubiconBannerExt;
import org.rtb.vexing.adapter.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.model.RubiconImpExt;
import org.rtb.vexing.adapter.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.model.RubiconParams;
import org.rtb.vexing.adapter.model.RubiconPubExt;
import org.rtb.vexing.adapter.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.model.RubiconSiteExt;
import org.rtb.vexing.adapter.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.model.RubiconTargetingExt;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderStatus;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class RubiconAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(RubiconAdapter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + HttpHeaderValues.CHARSET.toString() + "utf-8";
    public static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";

    private final String endpoint;
    private final String usersyncUrl;
    private final String xapiUsername;
    private final String xapiPassword;
    private final HttpClient httpClient;
    private final PublicSuffixList psl;

    private final URL endpointUrl;
    private final String authHeader;

    public RubiconAdapter(String endpoint, String usersyncUrl, String xapiUsername, String xapiPassword,
                          HttpClient httpClient, PublicSuffixList psl) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.usersyncUrl = Objects.requireNonNull(usersyncUrl);
        this.xapiUsername = Objects.requireNonNull(xapiUsername);
        this.xapiPassword = Objects.requireNonNull(xapiPassword);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.psl = Objects.requireNonNull(psl);

        endpointUrl = parseUrl(endpoint);
        authHeader = "Basic " + Base64.getEncoder().encodeToString((xapiUsername + ':' + xapiPassword).getBytes());
    }

    private static URL parseUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL supplied is not valid", e);
        }
    }

    @Override
    public Future<BidderResult> requestBids(Bidder bidder, PreBidRequest preBidRequest, HttpServerRequest httpRequest) {
        final List<Future> requestBidFutures = bidder.adUnitBids.stream()
                .map(adUnitBid -> requestSingleBid(adUnitBid, preBidRequest, httpRequest))
                .collect(Collectors.toList());

        final Future<BidderResult> bidderResultFuture = Future.future();
        // FIXME: error handling
        CompositeFuture.join(requestBidFutures).setHandler(requestBidsResult ->
                bidderResultFuture.complete(toBidderResult(bidder, requestBidsResult.result().list())));

        return bidderResultFuture;
    }

    private Future<Bid.BidBuilder> requestSingleBid(AdUnitBid adUnitBid, PreBidRequest preBidRequest,
                                                    HttpServerRequest httpRequest) {
        final RubiconParams rubiconParams = MAPPER.convertValue(adUnitBid.params, RubiconParams.class);

        final BidRequest bidRequest = BidRequest.builder()
                .id(preBidRequest.tid)
                .imp(Collections.singletonList(makeImp(adUnitBid, rubiconParams, httpRequest)))
                .site(makeSite(rubiconParams, httpRequest))
                .app(preBidRequest.app)
                .device(makeDevice(httpRequest))
                .user(makeUser(preBidRequest, httpRequest))
                .source(makeSource(preBidRequest))
                .at(1)
                .tmax(preBidRequest.timeoutMillis)
                .build();

        // FIXME: remove
        logger.debug("Bid request is {0}", Json.encodePrettily(bidRequest));

        final Future<Bid.BidBuilder> future = Future.future();
        httpClient.post(portFromUrl(endpointUrl), endpointUrl.getHost(), endpointUrl.getFile(),
                bidResponseHandler(future, adUnitBid))
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .putHeader(HttpHeaders.USER_AGENT, PREBID_SERVER_USER_AGENT)
                .setTimeout(preBidRequest.timeoutMillis)
                .exceptionHandler(throwable -> {
                    logger.error("Error occurred while sending bid request to an exchange", throwable);
                    if (!future.isComplete()) {
                        // FIXME: error handling
                        future.complete(null);
                    }
                })
                .end(Json.encode(bidRequest));
        return future;
    }

    private static Imp makeImp(AdUnitBid adUnitBid, RubiconParams rubiconParams, HttpServerRequest httpRequest) {
        return Imp.builder()
                .id(adUnitBid.adUnitCode)
                .secure(isSecure(httpRequest))
                .instl(adUnitBid.instl)
                .banner(makeBanner(adUnitBid))
                .ext(Json.mapper.valueToTree(makeImpExt(rubiconParams)))
                .build();
    }

    private static Integer isSecure(HttpServerRequest httpRequest) {
        return Objects.toString(httpRequest.headers().get("X-Forwarded-Proto")).equalsIgnoreCase("https")
                || httpRequest.scheme().equalsIgnoreCase("https")
                ? 1 : null;
    }

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        return Banner.builder()
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
                .format(adUnitBid.sizes)
                .topframe(adUnitBid.topframe)
                .ext(Json.mapper.valueToTree(makeBannerExt(adUnitBid.sizes)))
                .build();
    }

    private static RubiconBannerExt makeBannerExt(List<Format> sizes) {
        final List<Integer> rubiconSizeIds = sizes.stream()
                .map(RubiconSize::toId)
                .filter(id -> id > 0)
                .collect(Collectors.toList());
        return RubiconBannerExt.builder()
                .rp(RubiconBannerExtRp.builder()
                        // FIXME: error handling
                        .sizeId(rubiconSizeIds.get(0))
                        .altSizeIds(rubiconSizeIds.size() > 1
                                ? rubiconSizeIds.subList(1, rubiconSizeIds.size() - 1) : null)
                        .mime("text/html")
                        .build())
                .build();
    }

    private static RubiconImpExt makeImpExt(RubiconParams rubiconParams) {
        return RubiconImpExt.builder()
                .rp(RubiconImpExtRp.builder().zoneId(rubiconParams.zoneId).build())
                .build();
    }

    private Site makeSite(RubiconParams rubiconParams, HttpServerRequest httpRequest) {
        final String referrer = httpRequest.headers().get(HttpHeaders.REFERER);
        return Site.builder()
                .domain(domainFromUrl(referrer))
                .page(referrer)
                .publisher(makePublisher(rubiconParams))
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconParams)))
                .build();
    }

    private String domainFromUrl(String url) {
        try {
            // FIXME: error handling
            return psl.getRegistrableDomain(new URL(url).getHost());
        } catch (MalformedURLException e) {
            // FIXME: error handling
            throw new IllegalArgumentException();
        }
    }

    private static Publisher makePublisher(RubiconParams rubiconParams) {
        return Publisher.builder()
                .ext(Json.mapper.valueToTree(makePublisherExt(rubiconParams)))
                .build();
    }

    private static RubiconPubExt makePublisherExt(RubiconParams rubiconParams) {
        return RubiconPubExt.builder()
                .rp(RubiconPubExtRp.builder().accountId(rubiconParams.accountId).build())
                .build();
    }

    private static RubiconSiteExt makeSiteExt(RubiconParams rubiconParams) {
        return RubiconSiteExt.builder()
                .rp(RubiconSiteExtRp.builder().siteId(rubiconParams.siteId).build())
                .build();
    }

    private static Device makeDevice(HttpServerRequest httpRequest) {
        return Device.builder()
                .ua(httpRequest.headers().get(HttpHeaders.USER_AGENT))
                .ip(ipFromRequest(httpRequest))
                .build();
    }

    private static String ipFromRequest(HttpServerRequest httpRequest) {
        final String ip;

        final String xForwardedFor = Objects.toString(httpRequest.headers().get("X-Forwarded-For"), "");
        final String xRealIp = Objects.toString(httpRequest.headers().get("X-Real-IP"), "");
        if (!xForwardedFor.isEmpty()) {
            // X-Forwarded-For: client1, proxy1, proxy2
            final int commaInd = xForwardedFor.indexOf(',');
            ip = commaInd == -1 ? xForwardedFor : xForwardedFor.substring(0, commaInd);
        } else if (!xRealIp.isEmpty()) {
            ip = xRealIp;
        } else {
            ip = httpRequest.remoteAddress().host();
        }

        return ip;
    }

    private static User makeUser(PreBidRequest preBidRequest, HttpServerRequest httpRequest) {
        // buyeruid is parsed from the uids cookie
        // uids=eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dXMiOiIxMjM0NSJ9LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=
        // the server should parse the uids cookie and send it to the relevant adapter. i.e. the rubicon id goes only
        // to the rubicon adapter
        // id would be a UID for "adnxs" (see logic in open-source implementation)
        return preBidRequest.app != null ? preBidRequest.user : User.builder()
                .buyeruid("J7HUD05W-J-76F7")
                .id("-1")
                .build();
    }

    private static Source makeSource(PreBidRequest request) {
        return Source.builder()
                .fd(1)
                .tid(request.tid)
                .build();
    }

    private static int portFromUrl(URL url) {
        final int port = url.getPort();
        return port != -1 ? port : url.getDefaultPort();
    }

    private static Handler<HttpClientResponse> bidResponseHandler(Future<Bid.BidBuilder> future, AdUnitBid adUnitBid) {
        return response -> {
            if (response.statusCode() == 200) {
                response
                        .bodyHandler(buffer -> {
                            final String body = buffer.toString();
                            // FIXME: remove
                            logger.debug("Bid response body raw: {0}", body);
                            logger.debug("Bid response: {0}",
                                    Json.encodePrettily(Json.decodeValue(body, BidResponse.class)));
                            future.complete(toBidBuilder(Json.decodeValue(body, BidResponse.class), adUnitBid));
                        })
                        .exceptionHandler(exception -> {
                            logger.error("Error occurred during bid response handling", exception);
                            // FIXME: error handling
                            future.complete(null);
                        });
            } else {
                response.bodyHandler(buffer ->
                        logger.error("Bid response code is {0}, body: {1}",
                                response.statusCode(), buffer.toString()));
                // FIXME: error handling
                future.complete(null);
            }
        };
    }

    private static Bid.BidBuilder toBidBuilder(BidResponse bidResponse, AdUnitBid adUnitBid) {
        final com.iab.openrtb.response.Bid bid = bidResponse.getSeatbid().get(0).getBid().get(0);
        final RubiconTargetingExt rubiconTargetingExt = MAPPER.convertValue(bid.getExt(), RubiconTargetingExt.class);

        return Bid.builder()
                .code(bid.getImpid())
                .price(bid.getPrice()) // FIXME: now 0 is serialized as "0.0", but should be just "0"
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid())
                .adServerTargeting(toAdServerTargetingOrNull(rubiconTargetingExt))
                .bidder(adUnitBid.bidderCode)
                .bidId(adUnitBid.bidId);
    }

    private static Map<String, String> toAdServerTargetingOrNull(RubiconTargetingExt rubiconTargetingExt) {
        return rubiconTargetingExt.rp != null && rubiconTargetingExt.rp.targeting != null
                ? rubiconTargetingExt.rp.targeting.stream().collect(Collectors.toMap(t -> t.key, t -> t.values.get(0)))
                : null;
    }

    private static BidderResult toBidderResult(Bidder bidder, List<Bid.BidBuilder> requestBidResults) {
        return BidderResult.builder()
                .bidderStatus(BidderStatus.builder()
                        .bidder(bidder.bidderCode)
                        .numBids(requestBidResults.size())
                        .responseTime(10) // FIXME: compute response time for the bidderStatus
                        .build())
                .bids(requestBidResults.stream()
                        .map(b -> b.responseTime(10)) // FIXME: compute response time for the bidderStatus
                        .map(Bid.BidBuilder::build)
                        .collect(Collectors.toList()))
                .build();
    }
}
