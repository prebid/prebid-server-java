package org.prebid.server.handler;

import com.fasterxml.jackson.databind.node.TextNode;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.execution.TimeoutFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;


public class VtrackHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CacheService cacheService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private TimeoutFactory timeoutFactory;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    private VtrackHandler handler;

    @Before
    public void setUp() {
        handler = new VtrackHandler(cacheService, bidderCatalog, timeoutFactory, 2000);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.response().setStatusCode(anyInt())).willReturn(httpResponse);

        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));
        given(httpRequest.getParam("a")).willReturn("accountId");
    }

    @Test
    public void shouldRespondWithBadRequestWhenBodyIsEmpty() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Incoming request has no body"));
        verifyZeroInteractions(cacheService);
    }

    @Test
    public void shouldRespondWithBadRequestWhenBodyIsInvalid() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("none"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Failed to parse /vtrack request body"));
        verifyZeroInteractions(cacheService);
    }

    @Test
    public void shouldRespondWithInternalServerErrorWhenCacheServiceReturnFailure() {
        // given
        given(cacheService.cachePutObjects(any(), any(), any(), any()))
                .willReturn(Future.failedFuture("Timeout has been exceeded"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Timeout has been exceeded"));
    }

    @Test
    public void shouldTolerateRequestWithoutAccountParameterWhenNotContainsModifiedBidders() {
        // given
        given(httpRequest.getParam("a")).willReturn(null);
        given(cacheService.cachePutObjects(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(Collections.emptyList())));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(200));
        verify(httpResponse).end(eq("{\"responses\":[]}"));
    }

    @Test
    public void shouldRespondWithBadRequestWhenRequestAccountParameterIsMissingAndContainsModifiedBidders() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("" +
                "{\"puts\":[{\n" +
                "    \"bidid\": \"BIDID1\",\n" +
                "    \"bidder\": \"BIDDER\",\n" +
                "    \"value\": \"<VAST…/VAST>\"\n" +
                "}," +
                "{" +
                "    \"bidid\": \"BIDID2\",\n" +
                "    \"bidder\": \"UPDATABLE_BIDDER\",\n" +
                "    \"value\": \"<VAST…/VAST>\"\n" +
                "}]}"));
        given(bidderCatalog.isModifyingVastXmlAllowed("UPDATABLE_BIDDER")).willReturn(true);
        given(httpRequest.getParam("a")).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Request must contain 'a'=accountId parameter"));
        verifyZeroInteractions(cacheService);
    }

    @Test
    public void shouldRespondWithExpectedParameters() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("" +
                "{\"puts\":[{\n" +
                "    \"bidid\": \"BIDID1\",\n" +
                "    \"bidder\": \"BIDDER\",\n" +
                "    \"value\": \"<VAST…/VAST>\"\n" +
                "}," +
                "{" +
                "    \"bidid\": \"BIDID2\",\n" +
                "    \"bidder\": \"UPDATABLE_BIDDER\",\n" +
                "    \"value\": \"<VAST…/VAST>\"\n" +
                "}]}"));

        final BidCacheResponse cacheServiceResult = BidCacheResponse.of(Arrays.asList(
                CacheObject.of("uuid1"), CacheObject.of("uuid2")));

        given(bidderCatalog.isModifyingVastXmlAllowed("UPDATABLE_BIDDER")).willReturn(true);
        given(cacheService.cachePutObjects(anyList(), anyList(), anyString(), any()))
                .willReturn(Future.succeededFuture(cacheServiceResult));

        // when
        handler.handle(routingContext);

        // then
        final PutObject expectedFirstPut = PutObject.builder()
                .bidid("BIDID1")
                .bidder("BIDDER")
                .value(new TextNode("<VAST…/VAST>"))
                .build();
        final PutObject expectedSecondPut = PutObject.builder()
                .bidid("BIDID2")
                .bidder("UPDATABLE_BIDDER")
                .value(new TextNode("<VAST…/VAST>"))
                .build();

        final List<PutObject> expectedPuts = Arrays.asList(expectedFirstPut, expectedSecondPut);
        verify(cacheService).cachePutObjects(eq(expectedPuts), eq(Collections.singletonList("UPDATABLE_BIDDER")),
                eq("accountId"), any());

        verify(httpResponse).end(eq("{\"responses\":[{\"uuid\":\"uuid1\"},{\"uuid\":\"uuid2\"}]}"));
    }
}
