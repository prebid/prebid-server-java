package org.prebid.server.spring.config;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.handler.AuctionHandler;
import org.prebid.server.handler.BidderParamHandler;
import org.prebid.server.handler.CookieSyncHandler;
import org.prebid.server.handler.GetuidsHandler;
import org.prebid.server.handler.NoCacheHandler;
import org.prebid.server.handler.OptoutHandler;
import org.prebid.server.handler.SetuidHandler;
import org.prebid.server.handler.StatusHandler;
import org.prebid.server.handler.ValidateHandler;
import org.prebid.server.handler.info.BidderDetailsHandler;
import org.prebid.server.handler.info.BiddersHandler;
import org.prebid.server.handler.openrtb2.AmpHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class WebConfigurationTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private CookieHandler cookieHandler;
    @Mock
    private BodyHandler bodyHandler;
    @Mock
    private NoCacheHandler noCacheHandler;
    @Mock
    private CorsHandler corsHandler;
    @Mock
    private AuctionHandler auctionHandler;
    @Mock
    private org.prebid.server.handler.openrtb2.AuctionHandler openrtbAuctionHandler;
    @Mock
    private AmpHandler openrtbAmpHandler;
    @Mock
    private StatusHandler statusHandler;
    @Mock
    private CookieSyncHandler cookieSyncHandler;
    @Mock
    private SetuidHandler setuidHandler;
    @Mock
    private GetuidsHandler getuidsHandler;
    @Mock
    private OptoutHandler optoutHandler;
    @Mock
    private ValidateHandler validateHandler;
    @Mock
    private BidderParamHandler bidderParamHandler;
    @Mock
    private BiddersHandler biddersHandler;
    @Mock
    private BidderDetailsHandler bidderDetailsHandler;
    @Mock
    private StaticHandler staticHandler;


    @Test
    public void testExternalRoutesCanBeAdded() throws Exception {
        final Handler<RoutingContext> handler = mock(Handler.class);
        final Route[] expected = new Route[1];

        WebConfiguration webConfiguration = new WebConfiguration();
        webConfiguration.routesDefinitions = r -> {
            expected[0] = r.route("/robots.txt").handler(handler);
        };

        Router router = webConfiguration.router(vertx, cookieHandler, bodyHandler, noCacheHandler, corsHandler,
                auctionHandler, openrtbAuctionHandler, openrtbAmpHandler, statusHandler, cookieSyncHandler, setuidHandler,
                getuidsHandler, optoutHandler, validateHandler, bidderParamHandler, biddersHandler, bidderDetailsHandler,
                staticHandler);

        assertThat(router.getRoutes()).hasSize(21).contains(expected).last().matches(sh -> "/".equals(sh.getPath()));
    }

}