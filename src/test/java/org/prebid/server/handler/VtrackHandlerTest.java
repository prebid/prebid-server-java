package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;


public class VtrackHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private CacheService cacheService;
    @Mock
    private TimeoutFactory timeoutFactory;

    private VtrackHandler handler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpRequest.getParam("a")).willReturn("accountId");
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        handler = new VtrackHandler(
                2000, applicationSettings, bidderCatalog, cacheService, timeoutFactory, jacksonMapper);
    }

    @Test
    public void shouldRespondWithBadRequestWhenAccountParameterIsMissing() {
        // given
        given(httpRequest.getParam("a")).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings, cacheService);

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Account 'a' is required query parameter and can't be empty"));
    }

    @Test
    public void shouldRespondWithBadRequestWhenBodyIsEmpty() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings, cacheService);

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Incoming request has no body"));
    }

    @Test
    public void shouldRespondWithBadRequestWhenBodyCannotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("invalid"));

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings, cacheService);

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Failed to parse request body"));
    }

    @Test
    public void shouldRespondWithBadRequestWhenBidIdIsMissing() throws JsonProcessingException {
        // given
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(identity()));

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings, cacheService);

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("'bidid' is required field and can't be empty"));
    }

    @Test
    public void shouldRespondWithBadRequestWhenBidderIsMissing() throws JsonProcessingException {
        // given
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(builder -> builder.bidid("bidId")));

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings, cacheService);

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("'bidder' is required field and can't be empty"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorWhenFetchingAccountFails() throws JsonProcessingException {
        // given
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(builder -> builder.bidid("bidId").bidder("bidder")));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture("error"));

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(cacheService);

        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Error occurred while fetching account: error"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorWhenCacheServiceReturnFailure() throws JsonProcessingException {
        // given
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(builder -> builder.bidid("bidId").bidder("bidder")));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(true).build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any()))
                .willReturn(Future.failedFuture("error"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Error occurred while sending request to cache: error"));
    }

    @Test
    public void shouldTolerateNotFoundAccount() throws JsonProcessingException {
        // given
        final List<PutObject> putObjects = singletonList(
                PutObject.builder().bidid("bidId").bidder("bidder").value(new TextNode("value")).build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("not found")));
        given(cacheService.cachePutObjects(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(emptyList())));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheService).cachePutObjects(eq(putObjects), eq(emptySet()), eq("accountId"), any());
    }

    @Test
    public void shouldSendToCacheEmptyUpdatableBiddersIfAccountEventsEnabledIsNull() throws JsonProcessingException {
        // given
        final List<PutObject> putObjects = singletonList(
                PutObject.builder().bidid("bidId").bidder("bidder").value(new TextNode("value")).build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(null).build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(emptyList())));

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(bidderCatalog);

        verify(cacheService).cachePutObjects(eq(putObjects), eq(emptySet()), eq("accountId"), any());
    }

    @Test
    public void shouldSendToCacheExpectedPutsAndUpdatableBidders() throws JsonProcessingException {
        // given
        final List<PutObject> putObjects = asList(
                PutObject.builder().bidid("bidId1").bidder("bidder").value(new TextNode("value1")).build(),
                PutObject.builder().bidid("bidId2").bidder("updatable_bidder").value(new TextNode("value2")).build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(bidderCatalog.isModifyingVastXmlAllowed("updatable_bidder")).willReturn(true);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(true).build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(
                        asList(CacheObject.of("uuid1"), CacheObject.of("uuid2")))));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheService).cachePutObjects(eq(putObjects), eq(singleton("updatable_bidder")),
                eq("accountId"), any());

        verify(httpResponse).end(eq("{\"responses\":[{\"uuid\":\"uuid1\"},{\"uuid\":\"uuid2\"}]}"));
    }

    @SafeVarargs
    private static Buffer givenVtrackRequest(
            Function<PutObject.PutObjectBuilder, PutObject.PutObjectBuilder>... customizers) throws JsonProcessingException {

        final List<PutObject> putObjects;
        if (customizers != null) {
            putObjects = new ArrayList<>();
            for (Function<PutObject.PutObjectBuilder, PutObject.PutObjectBuilder> customizer : customizers) {
                putObjects.add(customizer.apply(PutObject.builder()).build());
            }
        } else {
            putObjects = null;
        }

        return givenVtrackRequest(putObjects);
    }

    private static Buffer givenVtrackRequest(List<PutObject> putObjects) throws JsonProcessingException {
        return Buffer.buffer(mapper.writeValueAsString(singletonMap("puts", putObjects)));
    }
}
