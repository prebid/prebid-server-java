package org.rtb.vexing.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
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
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;

public class RubiconAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(RubiconAdapter.class);

    private final String endpoint;
    private final String usersyncUrl;
    private final String xapiUsername;
    private final String xapiPassword;

    private final URL endpointUrl;
    private final String xapiCredentialsBase64;
    private String contentType =
            HttpHeaderValues.APPLICATION_JSON.toString() + HttpHeaderValues.CHARSET.toString() + "utf-8";

    private final ObjectMapper mapper = new ObjectMapper();

    public RubiconAdapter(String endpoint, String usersyncUrl, String xapiUsername, String xapiPassword) {
        this.endpoint = endpoint;
        this.usersyncUrl = usersyncUrl;
        this.xapiUsername = xapiUsername;
        this.xapiPassword = xapiPassword;

        endpointUrl = parseUrl(endpoint);
        xapiCredentialsBase64 = Base64.getEncoder().encodeToString((xapiUsername + ':' + xapiPassword).getBytes());
    }

    private static URL parseUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL supplied is not valid", e);
        }
    }

    @Override
    public Future<BidResponse> clientBid(HttpClient client, Bidder bidder, PreBidRequest request) {
        final RubiconParams rubiconParams = mapper.convertValue(bidder.params, RubiconParams.class);

        // these are the important fields to get an ad over XAPI: account_id, site_id, zone_id, buyeruid
        final RubiconImpExtRp rubiconImpExtRp = RubiconImpExtRp.builder()
                .zoneId(rubiconParams.zoneId)
                .build();
        final RubiconImpExt rubiconImpExt = RubiconImpExt.builder()
                .rp(rubiconImpExtRp)
                .build();

        final RubiconBannerExtRp rubiconBannerExtRp = RubiconBannerExtRp.builder()
                // FIXME
                .sizeId(15)
                .mime("text/html")
                .build();
        final RubiconBannerExt rubiconBannerExt = RubiconBannerExt.builder()
                .rp(rubiconBannerExtRp)
                .build();
        final Banner banner = Banner.builder()
                .format(bidder.sizes)
                .w(bidder.sizes.get(0).getW())
                .h(bidder.sizes.get(0).getH())
                // FIXME
                // .topframe(0)
                .ext(Json.mapper.valueToTree(rubiconBannerExt))
                .build();

        final Imp imp = Imp.builder()
                .id(bidder.code)
                // FIXME
                .secure(1)
                // FIXME
                //.instl(0)
                .banner(banner)
                .ext(Json.mapper.valueToTree(rubiconImpExt))
                .build();

        final RubiconPubExtRp rubiconPubExtRp = RubiconPubExtRp.builder()
                .accountId(rubiconParams.accountId)
                .build();
        final RubiconPubExt rubiconPubExt = RubiconPubExt.builder()
                .rp(rubiconPubExtRp)
                .build();
        final Publisher publisher = Publisher.builder()
                .ext(Json.mapper.valueToTree(rubiconPubExt))
                .build();

        final RubiconSiteExtRp rubiconSiteExtRp = RubiconSiteExtRp.builder()
                .siteId(rubiconParams.siteId)
                .build();
        final RubiconSiteExt rubiconSiteExt = RubiconSiteExt.builder()
                .rp(rubiconSiteExtRp)
                .build();

        final Site site = Site.builder()
                // FIXME
                .domain("whsv.com")
                // FIXME
                .page("http://www.whsv.com/")
                .publisher(publisher)
                .ext(Json.mapper.valueToTree(rubiconSiteExt))
                .build();

        final Device device = Device.builder()
                .ua("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/60.0.3112.113 Safari/537.36")
                .ip("69.141.166.108")
                .build();

        // FIXME: remove hardcoded value
        // buyeruid is parsed from the uids cookie
        // uids=eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dXMiOiIxMjM0NSJ9LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=
        // the server should parse the uids cookie and send it to the relevant adapter. i.e. the rubicon id goes only to the rubicon adapter
        final User user = User.builder()
                .buyeruid("J7HUD05W-J-76F7")
                // FIXME
                .id("-1")
                .build();

        final Source source = Source.builder()
                .fd(1)
                .tid(request.tid)
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .id(request.tid)
                .imp(Collections.singletonList(imp))
                .site(site)
                .app(request.app)
                .device(device)
                .user(user)
                .source(source)
                .at(1)
                .tmax(request.timeoutMillis)
                .build();

        // FIXME: remove
        logger.debug("Bid request is {0}", Json.encodePrettily(bidRequest));

        final Future<BidResponse> future = Future.future();
        client.post(getPort(endpointUrl), endpointUrl.getHost(), endpointUrl.getFile(), bidResponseHandler(future))
                .putHeader(HttpHeaders.AUTHORIZATION, "Basic " + xapiCredentialsBase64)
                .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .putHeader(HttpHeaders.USER_AGENT, "prebid-server/1.0")
                .setTimeout(request.timeoutMillis)
                .exceptionHandler(throwable -> {
                    logger.error("Error occurred while sending bid request to an exchange", throwable);
                    if (!future.isComplete())
                        future.complete(NO_BID_RESPONSE);
                })
                .end(Json.encode(bidRequest));
        return future;
    }

    private static int getPort(URL url) {
        final int port = url.getPort();
        return port != -1 ? port : url.getDefaultPort();
    }

    private static Handler<HttpClientResponse> bidResponseHandler(Future<BidResponse> future) {
        return response -> {
            if (response.statusCode() == 200) {
                response
                        .bodyHandler(buffer -> {
                            final String body = buffer.toString();
                            // FIXME: remove
                            logger.debug("Bid response body: {0}",
                                    Json.encodePrettily(Json.decodeValue(body, BidResponse.class)));
                            future.complete(Json.decodeValue(body, BidResponse.class));
                        })
                        .exceptionHandler(exception -> {
                            logger.error("Error occurred during bid response handling", exception);
                            future.complete(NO_BID_RESPONSE);
                        });
            } else {
                response.bodyHandler(buffer ->
                        logger.error("Bid response code is {0}, body: {1}",
                                response.statusCode(), buffer.toString()));
                future.complete(NO_BID_RESPONSE);
            }
        };
    }
}
