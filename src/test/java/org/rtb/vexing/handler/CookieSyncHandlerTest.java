package org.rtb.vexing.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.util.AsciiString;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.request.CookieSyncRequest;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.CookieSyncResponse;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.io.IOException;
import java.util.Arrays;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class CookieSyncHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AdapterCatalog adapterCatalog;
    @Mock
    private Adapter rubiconAdapter;
    @Mock
    private Adapter appnexusAdapter;
    @Mock
    private Metrics metrics;
    private CookieSyncHandler cookieSyncHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);

        cookieSyncHandler = new CookieSyncHandler(adapterCatalog, metrics);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new CookieSyncHandler(null, null));
        assertThatNullPointerException().isThrownBy(() -> new CookieSyncHandler(adapterCatalog, null));
    }

    @Test
    public void shouldRespondWithErrorIfOptedOut() {
        // given
        // this uids cookie value stands for {"optout": true}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "eyJvcHRvdXQiOiB0cnVlfQ=="));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        cookieSyncHandler.sync(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).setStatusMessage(eq("User has opted out"));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse, adapterCatalog);
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(null);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        cookieSyncHandler.sync(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse, adapterCatalog);
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        given(routingContext.getBodyAsJson())
                .willReturn(JsonObject.mapFrom(CookieSyncRequest.builder().bidders(emptyList()).build()));

        // when
        cookieSyncHandler.sync(routingContext);

        // then
        verify(httpResponse)
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithSomeBidderStatusesIfSomeUidsMissingInCookies() throws IOException {
        // given
        given(routingContext.getBodyAsJson()).willReturn(JsonObject.mapFrom(
                CookieSyncRequest.builder().uuid("uuid").bidders(Arrays.asList(RUBICON, APPNEXUS)).build()));

        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19"));

        givenAdaptersReturningFamilyName();

        final JsonNode appnexusUsersyncInfo = defaultNamingMapper.valueToTree(UsersyncInfo.builder()
                .url("http://adnxsexample.com")
                .type("redirect")
                .supportCORS(false)
                .build());
        given(appnexusAdapter.usersyncInfo()).willReturn(appnexusUsersyncInfo);

        // when
        cookieSyncHandler.sync(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.builder()
                .uuid("uuid")
                .status("OK")
                .bidderStatus(singletonList(BidderStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(appnexusUsersyncInfo)
                        .build()))
                .build());
    }

    @Test
    public void shouldRespondWithNoBidderStatusesIfAllUidsPresentInCookies() throws IOException {
        // given
        given(routingContext.getBodyAsJson()).willReturn(JsonObject.mapFrom(
                CookieSyncRequest.builder().uuid("uuid").bidders(Arrays.asList(RUBICON, APPNEXUS)).build()));

        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT", "adnxs": "12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwgImFkbnhzIjogIjEyMzQ1In19"));

        givenAdaptersReturningFamilyName();

        // when
        cookieSyncHandler.sync(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.builder()
                .uuid("uuid")
                .status("OK")
                .bidderStatus(emptyList())
                .build());
    }

    @Test
    public void shouldIncrementMetrics() {
        // given
        given(routingContext.getBodyAsJson())
                .willReturn(JsonObject.mapFrom(CookieSyncRequest.builder().bidders(emptyList()).build()));

        // when
        cookieSyncHandler.sync(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.cookie_sync_requests));
    }

    private void givenAdaptersReturningFamilyName() {
        given(adapterCatalog.get(eq(RUBICON))).willReturn(rubiconAdapter);
        given(adapterCatalog.get(eq(APPNEXUS))).willReturn(appnexusAdapter);

        given(rubiconAdapter.familyName()).willReturn("rubicon");
        given(appnexusAdapter.familyName()).willReturn("adnxs");
    }

    private CookieSyncResponse captureCookieSyncResponse() throws IOException {
        final ArgumentCaptor<String> cookieSyncResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(cookieSyncResponseCaptor.capture());
        return mapper.readValue(cookieSyncResponseCaptor.getValue(), CookieSyncResponse.class);
    }
}
