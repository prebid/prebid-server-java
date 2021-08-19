package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
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
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.HashSet;
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
import static org.mockito.ArgumentMatchers.isNull;
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
        given(httpResponse.putHeader(any(CharSequence.class), any(AsciiString.class))).willReturn(httpResponse);

        given(httpRequest.getParam("a")).willReturn("accountId");
        given(httpRequest.getParam("int")).willReturn("pbjs");

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        handler = new VtrackHandler(
                2000, true, true, applicationSettings, bidderCatalog, cacheService, timeoutFactory, jacksonMapper);
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
    public void shouldRespondWithExpectedHeaders() {
        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
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
    public void shouldRespondWithBadRequestWhenTypeIsNotXML() throws JsonProcessingException {
        // given
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(builder -> builder.bidid("bidId").bidder("bidder").type("json")));

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings, cacheService);

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("vtrack only accepts type xml"));
    }

    @Test
    public void shouldRespondWithBadRequestWhenValueDoesNotContainVast() throws JsonProcessingException {
        // given
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(builder -> builder.bidid("bidId")
                        .bidder("bidder")
                        .type("xml")
                        .value(new TextNode("invalidValue"))));

        // when
        handler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings, cacheService);

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("vtrack content must be vast"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorWhenFetchingAccountFails() throws JsonProcessingException {
        // given
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(builder -> builder
                        .bidder("bidder")
                        .bidid("bidId")
                        .type("xml")
                        .value(new TextNode("<Vast"))));

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
                .willReturn(givenVtrackRequest(builder -> builder
                        .bidder("bidder")
                        .bidid("bidId")
                        .type("xml")
                        .value(new TextNode("<Vast"))));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any(), any(), any()))
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
                PutObject.builder()
                        .bidid("bidId")
                        .bidder("bidder")
                        .type("xml")
                        .value(new TextNode("<vast")).build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("not found")));
        given(cacheService.cachePutObjects(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(emptyList())));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheService).cachePutObjects(eq(putObjects), any(), eq(singleton("bidder")), eq("accountId"),
                eq("pbjs"), any());
    }

    @Test
    public void shouldSendToCacheNullInAccountEnabledAndValidBiddersWhenAccountEventsEnabledIsNull()
            throws JsonProcessingException {
        // given
        final List<PutObject> putObjects = singletonList(
                PutObject.builder()
                        .bidid("bidId")
                        .bidder("bidder")
                        .type("xml")
                        .value(new TextNode("<vast")).build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder().build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(emptyList())));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheService).cachePutObjects(eq(putObjects), isNull(), eq(singleton("bidder")), eq("accountId"),
                eq("pbjs"), any());
    }

    @Test
    public void shouldSendToCacheExpectedPutsAndUpdatableBiddersWhenBidderVastNotAllowed()
            throws JsonProcessingException {
        // given
        handler = new VtrackHandler(
                2000, false, true, applicationSettings, bidderCatalog, cacheService, timeoutFactory, jacksonMapper);

        final List<PutObject> putObjects = asList(
                PutObject.builder().bidid("bidId1")
                        .bidder("bidder")
                        .type("xml")
                        .value(new TextNode("<vast"))
                        .build(),
                PutObject.builder()
                        .bidid("bidId2")
                        .bidder("updatable_bidder")
                        .type("xml")
                        .value(new TextNode("<vast")).build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(bidderCatalog.isValidName("bidder")).willReturn(true);
        given(bidderCatalog.isModifyingVastXmlAllowed("bidder")).willReturn(false);
        given(bidderCatalog.isValidName("updatable_bidder")).willReturn(true);
        given(bidderCatalog.isModifyingVastXmlAllowed("updatable_bidder")).willReturn(true);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(
                        singletonList(CacheObject.of("uuid1")))));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheService).cachePutObjects(
                eq(putObjects), any(), eq(singleton("updatable_bidder")), eq("accountId"), eq("pbjs"), any());

        verify(httpResponse).end(eq("{\"responses\":[{\"uuid\":\"uuid1\"}]}"));
    }

    @Test
    public void shouldSendToCacheExpectedPutsAndUpdatableBiddersWhenBidderVastAllowed() throws JsonProcessingException {
        // given
        handler = new VtrackHandler(
                2000, false, false, applicationSettings, bidderCatalog, cacheService, timeoutFactory, jacksonMapper);

        final List<PutObject> putObjects = asList(
                PutObject.builder().bidid("bidId1")
                        .bidder("bidder")
                        .type("xml")
                        .value(new TextNode("<Vast")).build(),
                PutObject.builder()
                        .bidid("bidId2")
                        .bidder("updatable_bidder")
                        .type("xml")
                        .value(new TextNode("<vast")).build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(bidderCatalog.isValidName(any())).willReturn(true);
        given(bidderCatalog.isModifyingVastXmlAllowed(any())).willReturn(true);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(
                        asList(CacheObject.of("uuid1"), CacheObject.of("uuid2")))));

        // when
        handler.handle(routingContext);

        // then
        final HashSet<String> expectedBidders = new HashSet<>(asList("bidder", "updatable_bidder"));
        verify(cacheService).cachePutObjects(eq(putObjects), any(), eq(expectedBidders), eq("accountId"), eq("pbjs"),
                any());

        verify(httpResponse).end(eq("{\"responses\":[{\"uuid\":\"uuid1\"},{\"uuid\":\"uuid2\"}]}"));
    }

    @Test
    public void shouldSendToCacheExpectedPutsWhenModifyVastForUnknownBidderAndAllowUnknownBidderIsTrue()
            throws JsonProcessingException {
        // given
        final List<PutObject> putObjects = asList(
                PutObject.builder()
                        .bidid("bidId1")
                        .bidder("bidder")
                        .type("xml")
                        .value(new TextNode("<vast"))
                        .build(),
                PutObject.builder()
                        .bidid("bidId2")
                        .bidder("updatable_bidder")
                        .type("xml")
                        .value(new TextNode("<Vast"))
                        .build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(bidderCatalog.isValidName(any())).willReturn(false);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(
                        asList(CacheObject.of("uuid1"), CacheObject.of("uuid2")))));

        // when
        handler.handle(routingContext);

        // then
        final HashSet<String> expectedBidders = new HashSet<>(asList("bidder", "updatable_bidder"));
        verify(cacheService).cachePutObjects(eq(putObjects), any(), eq(expectedBidders), eq("accountId"), eq("pbjs"),
                any());

        verify(httpResponse).end(eq("{\"responses\":[{\"uuid\":\"uuid1\"},{\"uuid\":\"uuid2\"}]}"));
    }

    @Test
    public void shouldSendToCacheWithEmptyBiddersAllowingVastUpdatePutsWhenAllowUnknownBidderIsFalse()
            throws JsonProcessingException {
        // given
        handler = new VtrackHandler(
                2000, false, true, applicationSettings, bidderCatalog, cacheService, timeoutFactory, jacksonMapper);

        final List<PutObject> putObjects = asList(
                PutObject.builder()
                        .bidid("bidId1")
                        .bidder("bidder")
                        .type("xml")
                        .value(new TextNode("<vast"))
                        .build(),
                PutObject.builder()
                        .bidid("bidId2")
                        .bidder("updatable_bidder")
                        .type("xml")
                        .value(new TextNode("<Vast"))
                        .build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(bidderCatalog.isValidName(any())).willReturn(false);
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(true).build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(
                        asList(CacheObject.of("uuid1"), CacheObject.of("uuid2")))));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheService).cachePutObjects(any(), any(), eq(emptySet()), any(), any(), any());
    }

    @Test
    public void shouldSendToCacheWithEmptyBiddersAllowingVastUpdatePutsWhenModifyVastForUnknownBidderIsFalse()
            throws JsonProcessingException {
        // given
        handler = new VtrackHandler(
                2000, true, false, applicationSettings, bidderCatalog, cacheService, timeoutFactory, jacksonMapper);

        final List<PutObject> putObjects = asList(
                PutObject.builder()
                        .bidid("bidId1")
                        .bidder("bidder")
                        .type("xml")
                        .value(new TextNode("<vast"))
                        .build(),
                PutObject.builder()
                        .bidid("bidId2")
                        .bidder("updatable_bidder")
                        .type("xml")
                        .value(new TextNode("<Vast"))
                        .build());
        given(routingContext.getBody())
                .willReturn(givenVtrackRequest(putObjects));

        given(bidderCatalog.isValidName(any())).willReturn(false);
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(true).build()));
        given(cacheService.cachePutObjects(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidCacheResponse.of(
                        asList(CacheObject.of("uuid1"), CacheObject.of("uuid2")))));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheService).cachePutObjects(any(), any(), eq(emptySet()), any(), any(), any());
    }

    @SafeVarargs
    private static Buffer givenVtrackRequest(
            Function<PutObject.PutObjectBuilder, PutObject.PutObjectBuilder>... customizers)
            throws JsonProcessingException {

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
