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
import io.netty.handler.codec.http.HttpHeaderValues;
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
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;

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

    private final URL endpointUrl;
    private final String authHeader;

    public RubiconAdapter(String endpoint, String usersyncUrl, String xapiUsername, String xapiPassword,
                          HttpClient httpClient) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.usersyncUrl = Objects.requireNonNull(usersyncUrl);
        this.xapiUsername = Objects.requireNonNull(xapiUsername);
        this.xapiPassword = Objects.requireNonNull(xapiPassword);
        this.httpClient = Objects.requireNonNull(httpClient);

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
    public Future<Bid> clientBid(Bidder bidder, PreBidRequest preBidRequest, HttpServerRequest httpRequest) {
        final RubiconParams rubiconParams = MAPPER.convertValue(bidder.params, RubiconParams.class);

        final BidRequest bidRequest = BidRequest.builder()
                .id(preBidRequest.tid)
                .imp(Collections.singletonList(makeImp(bidder, rubiconParams, httpRequest)))
                .site(makeSite(rubiconParams))
                .app(preBidRequest.app)
                .device(makeDevice())
                .user(makeUser())
                .source(makeSource(preBidRequest))
                .at(1)
                .tmax(preBidRequest.timeoutMillis)
                .build();

        // FIXME: remove
        logger.debug("Bid request is {0}", Json.encodePrettily(bidRequest));

        final Future<Bid> future = Future.future();
        httpClient.post(getPort(endpointUrl), endpointUrl.getHost(), endpointUrl.getFile(),
                bidResponseHandler(future, bidder))
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .putHeader(HttpHeaders.USER_AGENT, PREBID_SERVER_USER_AGENT)
                .setTimeout(preBidRequest.timeoutMillis)
                .exceptionHandler(throwable -> {
                    logger.error("Error occurred while sending bid request to an exchange", throwable);
                    if (!future.isComplete()) {
                        // FIXME
                        future.complete(null);
                    }
                })
                .end(Json.encode(bidRequest));
        return future;
    }

    private static Imp makeImp(Bidder bidder, RubiconParams rubiconParams, HttpServerRequest httpRequest) {
        return Imp.builder()
                .id(bidder.adUnitCode)
                .secure(isSecure(httpRequest))
                .instl(bidder.instl)
                .banner(makeBanner(bidder))
                .ext(Json.mapper.valueToTree(makeImpExt(rubiconParams)))
                .build();
    }

    private static Integer isSecure(HttpServerRequest httpRequest) {
        return Objects.toString(httpRequest.headers().get("X-Forwarded-Proto")).equalsIgnoreCase("https")
                || httpRequest.scheme().equalsIgnoreCase("https")
                ? 1 : null;
    }

    private static Banner makeBanner(Bidder bidder) {
        return Banner.builder()
                .w(bidder.sizes.get(0).getW())
                .h(bidder.sizes.get(0).getH())
                .format(bidder.sizes)
                .topframe(bidder.topframe)
                .ext(Json.mapper.valueToTree(makeBannerExt(bidder.sizes)))
                .build();
    }

    private static RubiconBannerExt makeBannerExt(List<Format> sizes) {
        final List<Integer> rubiconSizeIds = sizes.stream()
                .map(RubiconSize::toId)
                .filter(id -> id > 0)
                .collect(Collectors.toList());
        return RubiconBannerExt.builder()
                .rp(RubiconBannerExtRp.builder()
                        // FIXME error handling
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

    private static Site makeSite(RubiconParams rubiconParams) {
        return Site.builder()
                // FIXME
                .domain("whsv.com")
                // FIXME
                .page("http://www.whsv.com/")
                .publisher(makePublisher(rubiconParams))
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconParams)))
                .build();
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

    private static Device makeDevice() {
        // FIXME: remove hardcoded value
        return Device.builder()
                .ua("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/60.0.3112.113 Safari/537.36")
                .ip("69.141.166.108")
                .build();
    }

    private static User makeUser() {
        // buyeruid is parsed from the uids cookie
        // uids=eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dXMiOiIxMjM0NSJ9LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=
        // the server should parse the uids cookie and send it to the relevant adapter. i.e. the rubicon id goes only
        // to the rubicon adapter
        return User.builder()
                .buyeruid("J7HUD05W-J-76F7")
                // FIXME
                .id("-1")
                .build();
    }

    private static Source makeSource(PreBidRequest request) {
        return Source.builder()
                .fd(1)
                .tid(request.tid)
                .build();
    }

    private static int getPort(URL url) {
        final int port = url.getPort();
        return port != -1 ? port : url.getDefaultPort();
    }

    private static Handler<HttpClientResponse> bidResponseHandler(Future<Bid> future, Bidder bidder) {
        return response -> {
            if (response.statusCode() == 200) {
                response
                        .bodyHandler(buffer -> {
                            final String body = buffer.toString();
                            // FIXME: remove
                            logger.debug("Bid response body raw: {0}", body);
                            logger.debug("Bid response: {0}",
                                    Json.encodePrettily(Json.decodeValue(body, BidResponse.class)));
                            future.complete(toBid(Json.decodeValue(body, BidResponse.class), bidder));
                        })
                        .exceptionHandler(exception -> {
                            logger.error("Error occurred during bid response handling", exception);
                            // FIXME
                            future.complete(null);
                        });
            } else {
                response.bodyHandler(buffer ->
                        logger.error("Bid response code is {0}, body: {1}",
                                response.statusCode(), buffer.toString()));
                // FIXME
                future.complete(null);
            }
        };
    }

    private static Bid toBid(BidResponse bidResponse, Bidder bidder) {
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
                .bidder(bidder.bidderCode)
                .bidId(bidder.bidId)
                .responseTime(10) // FIXME
                .build();
    }

    private static Map<String, String> toAdServerTargetingOrNull(RubiconTargetingExt rubiconTargetingExt) {
        return rubiconTargetingExt.rp != null && rubiconTargetingExt.rp.targeting != null
                ? rubiconTargetingExt.rp.targeting.stream().collect(Collectors.toMap(t -> t.key, t -> t.values.get(0)))
                : null;
    }
}
