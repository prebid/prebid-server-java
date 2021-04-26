package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class AuctionRequestFactoryTest extends VertxTest {

    private static final String ACCOUNT_ID = "acc_id";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Ortb2RequestFactory ortb2RequestFactory;
    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private ImplicitParametersExtractor paramsExtractor;
    @Mock
    private Ortb2ImplicitParametersResolver paramsResolver;
    @Mock
    private InterstitialProcessor interstitialProcessor;
    @Mock
    private OrtbTypesResolver ortbTypesResolver;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private TimeoutResolver timeoutResolver;

    @Mock
    private AuctionRequestFactory target;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;

    private Account defaultAccount;
    private BidRequest defaultBidRequest;
    private AuctionContext defaultActionContext;

    @Before
    public void setUp() {
        defaultBidRequest = BidRequest.builder().build();
        defaultAccount = Account.empty(ACCOUNT_ID);

        final PrivacyContext defaultPrivacyContext = PrivacyContext.of(
                Privacy.of("0", EMPTY, Ccpa.EMPTY, 0),
                TcfContext.empty());
        defaultActionContext = AuctionContext.builder()
                .bidRequest(defaultBidRequest)
                .account(defaultAccount)
                .prebidErrors(new ArrayList<>())
                .privacyContext(defaultPrivacyContext)
                .build();

        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(timeoutResolver.resolve(any())).willReturn(2000L);
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(1900L);

        given(paramsResolver.resolve(any(), any(), any()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));
        given(ortb2RequestFactory.validateRequest(any()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));
        given(interstitialProcessor.process(any()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));

        given(privacyEnforcementService.contextFromBidRequest(any()))
                .willReturn(Future.succeededFuture(defaultPrivacyContext));

        given(ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(any(), any(), any()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));

        target = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                ortb2RequestFactory,
                storedRequestProcessor,
                paramsExtractor,
                paramsResolver,
                interstitialProcessor,
                ortbTypesResolver,
                privacyEnforcementService,
                timeoutResolver,
                jacksonMapper);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Incoming request has no body");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyExceedsMaxRequestSize() {
        // given
        target = new AuctionRequestFactory(
                1,
                ortb2RequestFactory,
                storedRequestProcessor,
                paramsExtractor,
                paramsResolver,
                interstitialProcessor,
                ortbTypesResolver,
                privacyEnforcementService,
                timeoutResolver,
                jacksonMapper);

        given(routingContext.getBodyAsString()).willReturn("body");

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request size exceeded max size of 1 bytes.");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBodyAsString()).willReturn("body");

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).hasSize(1)
                .element(0).asString().startsWith("Error decoding bidRequest: Unrecognized token 'body'");
    }

    @Test
    public void shouldReturnFailedFutureIfEidsPermissionsContainsWrongDataType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(emptyList(), null))
                        .build()))
                .build();

        final ObjectNode requestNode = mapper.convertValue(bidRequest, ObjectNode.class);
        final JsonNode eidPermissionNode = mapper.convertValue(
                ExtRequestPrebidDataEidPermissions.of("source", emptyList()), JsonNode.class);

        requestNode.with("ext").with("prebid").with("data").set("eidpermissions", eidPermissionNode);

        given(routingContext.getBodyAsString()).willReturn(requestNode.toString());

        // when
        final Future<?> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) result.cause()).getMessages()).hasSize(1)
                .allSatisfy(message ->
                        assertThat(message).startsWith("Error decoding bidRequest: Cannot deserialize instance"));
    }

    @Test
    public void shouldReturnFailedFutureIfEidsPermissionsBiddersContainsWrongDataType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(emptyList(), null))
                        .build()))
                .build();

        final ObjectNode requestNode = mapper.convertValue(bidRequest, ObjectNode.class);

        final ObjectNode eidPermissionNode = mapper.convertValue(
                ExtRequestPrebidDataEidPermissions.of("source", emptyList()), ObjectNode.class);

        eidPermissionNode.put("bidders", "notArrayValue");

        final ArrayNode arrayNode = requestNode
                .with("ext")
                .with("prebid")
                .with("data")
                .putArray("eidpermissions");
        arrayNode.add(eidPermissionNode);

        given(routingContext.getBodyAsString()).willReturn(requestNode.toString());

        // when
        final Future<?> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) result.cause()).getMessages()).hasSize(1)
                .allSatisfy(message ->
                        assertThat(message).startsWith("Error decoding bidRequest: Cannot deserialize instance"));
    }

    @Test
    public void shouldTolerateMissingImpExtWhenProcessingAliases() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(null).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("alias", "bidder"))
                        .build()))
                .build();
        givenBidRequest(bidRequest);
        givenAuctionContext(bidRequest, defaultAccount);
        givenProcessStoredRequest(bidRequest);

        // when
        final Future<?> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void shouldCallOrtbFieldsResolver() {
        // given
        givenValidBidRequest();

        // when
        target.fromRequest(routingContext, 0L).result();

        // then
        verify(ortbTypesResolver).normalizeBidRequest(any(), any(), any());
    }

    @Test
    public void shouldReturnFailedFutureIfOrtb2RequestFactoryReturnedFailedFuture() {
        // given
        givenBidRequest(BidRequest.builder().build());
        given(ortb2RequestFactory.fetchAccountAndCreateAuctionContext(any(), any(), any(), anyBoolean(), anyLong(),
                any())).willReturn(Future.failedFuture("error"));

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("error");
    }

    @Test
    public void shouldPassWebRequestTypeMetricToFetchAccountWhenSiteIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().site(Site.builder().build()).build();
        givenBidRequest(bidRequest);
        givenAuctionContext(bidRequest, Account.empty(ACCOUNT_ID));

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(ortb2RequestFactory).fetchAccountAndCreateAuctionContext(eq(routingContext), eq(bidRequest),
                eq(MetricName.openrtb2web), eq(true), anyLong(), any());
    }

    @Test
    public void shouldPassAppRequestTypeMetricToFetchAccountWhenAppIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().app(App.builder().build()).build();
        givenBidRequest(bidRequest);
        givenAuctionContext(bidRequest, Account.empty(ACCOUNT_ID));

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(ortb2RequestFactory).fetchAccountAndCreateAuctionContext(eq(routingContext), eq(bidRequest),
                eq(MetricName.openrtb2app), eq(true), anyLong(), any());
    }

    @Test
    public void storedRequestProcessorShouldUseAccountIdFetchedByOrtb2RequestFactory() {
        // given
        givenValidBidRequest();

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(storedRequestProcessor).processStoredRequests(eq(ACCOUNT_ID), any());
    }

    @Test
    public void shouldReturnFailedFutureIfProcessStoredRequestsFailed() {
        // given
        givenValidBidRequest();
        given(storedRequestProcessor.processStoredRequests(any(), any()))
                .willReturn(Future.failedFuture("error"));

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("error");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestValidationFailed() {
        // given
        givenValidBidRequest();

        given(ortb2RequestFactory.validateRequest(any()))
                .willThrow(new InvalidRequestException("errors"));

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).containsOnly("errors");
    }

    @Test
    public void shouldReturnAuctionContextWithExpectedParameters() {
        // given
        givenValidBidRequest();

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result).isEqualTo(defaultActionContext);
    }

    @Test
    public void shouldReturnModifiedBidRequestInAuctionContextWhenRequestWasPopulatedWithImplicitParams() {
        // given
        givenValidBidRequest();

        final BidRequest updatedBidRequest = defaultBidRequest.toBuilder().id("updated").build();
        given(paramsResolver.resolve(any(), any(), any())).willReturn(updatedBidRequest);

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getBidRequest()).isEqualTo(updatedBidRequest);
    }

    @Test
    public void shouldReturnPopulatedPrivacyContextAndGetWhenPrivacyEnforcementReturnContext() {
        // given
        givenValidBidRequest();

        final GeoInfo geoInfo = GeoInfo.builder().vendor("vendor").city("found").build();
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.of("1", "consent", Ccpa.EMPTY, 0),
                TcfContext.builder().geoInfo(geoInfo).build());
        given(privacyEnforcementService.contextFromBidRequest(any()))
                .willReturn(Future.succeededFuture(privacyContext));

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getPrivacyContext()).isEqualTo(privacyContext);
        assertThat(result.getGeoInfo()).isEqualTo(geoInfo);
    }

    private void givenBidRequest(BidRequest bidRequest) {
        try {
            given(routingContext.getBodyAsString()).willReturn(mapper.writeValueAsString(bidRequest));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void givenAuctionContext(BidRequest bidRequest, Account account) {
        given(ortb2RequestFactory.fetchAccountAndCreateAuctionContext(any(), any(), any(), anyBoolean(), anyLong(),
                any()))
                .willReturn(Future.succeededFuture(defaultActionContext.toBuilder()
                        .bidRequest(bidRequest)
                        .account(account)
                        .build()));
    }

    private void givenProcessStoredRequest(BidRequest bidRequest) {
        given(storedRequestProcessor.processStoredRequests(any(), any()))
                .willReturn(Future.succeededFuture(bidRequest));
    }

    private void givenValidBidRequest() {
        givenBidRequest(defaultBidRequest);
        givenAuctionContext(defaultBidRequest, defaultAccount);
        givenProcessStoredRequest(defaultBidRequest);
    }
}
