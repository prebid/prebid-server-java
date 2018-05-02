package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class AuctionRequestFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private ImplicitParametersExtractor paramsExtractor;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private RequestValidator requestValidator;

    private AuctionRequestFactory factory;

    @Mock
    private RoutingContext routingContext;

    @Before
    public void setUp() {
        factory = new AuctionRequestFactory(Integer.MAX_VALUE, "USD", storedRequestProcessor, paramsExtractor,
                uidsCookieService, requestValidator);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .containsOnly("Incoming request has no body");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyExceedsMaxRequestSize() {
        // given
        factory = new AuctionRequestFactory(1, "USD", storedRequestProcessor, paramsExtractor,
                uidsCookieService, requestValidator);

        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .containsOnly("Request size exceeded max size of 1 bytes.");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).hasSize(1)
                .element(0).asString().startsWith("Failed to decode:");
    }

    @Test
    public void shouldSetFieldsFromHeadersIfBodyFieldsEmpty() {
        // given
        givenValidBidRequest();

        givenImplicitParams("http://example.com", "example.com", "192.168.244.1", "UnitTest");
        given(uidsCookieService.parseHostCookie(any())).willReturn("userId");

        // when
        final BidRequest populatedBidRequest = factory.fromRequest(routingContext).result();

        // then
        assertThat(populatedBidRequest.getSite()).isEqualTo(
                Site.builder().page("http://example.com").domain("example.com").build());
        assertThat(populatedBidRequest.getDevice()).isEqualTo(
                Device.builder().ip("192.168.244.1").ua("UnitTest").build());
        assertThat(populatedBidRequest.getUser()).isEqualTo(User.builder().id("userId").build());
    }

    @Test
    public void shouldNotSetFieldsFromHeadersIfRequestFieldsNotEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("test.com").page("http://test.com").build())
                .device(Device.builder().ua("UnitTestUA").ip("56.76.12.3").build())
                .user(User.builder().id("userId").build())
                .cur(singletonList("USD"))
                .at(1)
                .build();

        givenBidRequest(bidRequest);

        givenImplicitParams("http://anotherexample.com", "anotherexample.com", "192.168.244.2", "UnitTest2");
        given(uidsCookieService.parseHostCookie(any())).willReturn("userId");

        // when
        final BidRequest populatedBidRequest = factory.fromRequest(routingContext).result();

        // then
        assertThat(populatedBidRequest).isSameAs(bidRequest);
    }

    @Test
    public void shouldNotSetSiteIfNoReferer() {
        // given
        givenValidBidRequest();

        // when
        final BidRequest populatedBidRequest = factory.fromRequest(routingContext).result();

        // then
        assertThat(populatedBidRequest.getSite()).isNull();
    }

    @Test
    public void shouldNotSetSitePageIfNoReferer() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder().domain("home.com").build());
    }

    @Test
    public void shouldNotSetSitePageIfDomainCouldNotBeDerived() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build());

        given(paramsExtractor.domainFrom(anyString())).willThrow(new PreBidException("Couldn't derive domain"));

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder().domain("home.com").build());
    }

    @Test
    public void shouldNotSetUserIfNoHostCookie() {
        // given
        givenValidBidRequest();

        given(uidsCookieService.parseHostCookie(any())).willReturn(null);

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void shouldSetDefaultAtIfInitialValueIsEqualsToZero() {
        // given
        givenBidRequest(BidRequest.builder().at(0).build());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then
        assertThat(result.getAt()).isEqualTo(1);
    }

    @Test
    public void shouldSetDefaultAtIfInitialValueIsEqualsToNull() {
        // given
        givenBidRequest(BidRequest.builder().at(null).build());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then
        assertThat(result.getAt()).isEqualTo(1);
    }

    @Test
    public void shouldSetCurrencyIfMissedInRequestAndPresentInAdServerCurrencyConfig(){
        // given
        givenBidRequest(BidRequest.builder().cur(null).build());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then
        assertThat(result.getCur()).isEqualTo(singletonList("USD"));
    }

    @Test
    public void shouldConvertStringPriceGranularityViewToCustom() throws JsonProcessingException {
        // given
        givenBidRequest(BidRequest.builder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, ExtRequestTargeting.of(new TextNode("low"), null, null), null, null))))
                .build());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then

        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity)
                .containsOnly(mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                        BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))));
    }

    @Test
    public void shouldReturnFailedFutureWithInvalidRequestExceptionWhenStringPriceGranularityInvalid() {
        // given
        givenBidRequest(BidRequest.builder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, ExtRequestTargeting.of(new TextNode("invalid"), null, null), null, null))))
                .build());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .containsOnly("Invalid string price granularity with value: invalid");
    }

    @Test
    public void shouldSetDefaultPriceGranularityIfPriceGranularityNodeIsMissed() {
        // given
        givenBidRequest(BidRequest.builder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, ExtRequestTargeting.of(null, null, null), null, null))))
                .build());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then

        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity)
                .containsOnly(mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                        BigDecimal.valueOf(20), BigDecimal.valueOf(0.1))))));
    }

    @Test
    public void shouldSetDefaultIncludeWinnersIfIncludeWinnersIsMissed() {
        // given
        givenBidRequest(BidRequest.builder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, ExtRequestTargeting.of(null, null, null), null, null))))
                .build());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then

        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners)
                .containsOnly(true);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestValidationFailed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future.succeededFuture(
                BidRequest.builder().build()));

        given(requestValidator.validate(any())).willReturn(new ValidationResult(asList("error1", "error2")));

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).containsOnly("error1", "error2");
    }

    private void givenImplicitParams(String referer, String domain, String ip, String ua) {
        given(paramsExtractor.refererFrom(any())).willReturn(referer);
        given(paramsExtractor.domainFrom(anyString())).willReturn(domain);
        given(paramsExtractor.ipFrom(any())).willReturn(ip);
        given(paramsExtractor.uaFrom(any())).willReturn(ua);
    }

    private void givenBidRequest(BidRequest bidRequest) {
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future.succeededFuture(bidRequest));

        given(requestValidator.validate(any())).willReturn(ValidationResult.success());
    }

    private void givenValidBidRequest() {
        givenBidRequest(BidRequest.builder().build());
    }
}