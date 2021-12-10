package org.prebid.server.events;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class EventUtilTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
    }

    @Test
    public void validateTypeShouldFailWhenTypeIsMissing() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateType(routingContext))
                .withMessage("Type 't' is required query parameter. Possible values are win and imp, but was null");
    }

    @Test
    public void validateTypeShouldFailWhenTypeIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "invalid"));

        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateType(routingContext))
                .withMessage("Type 't' is required query parameter. Possible values are win and imp, but was invalid");
    }

    @Test
    public void validateTypeShouldSucceedWhenTypeIsWin() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win"));

        // when and then
        assertThatCode(() -> EventUtil.validateType(routingContext))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateTypeShouldSucceedWhenTypeIsImp() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "imp"));

        // when and then
        assertThatCode(() -> EventUtil.validateType(routingContext))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateAccountIdShouldFailWhenAccountIsMissing() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateAccountId(routingContext))
                .withMessage("Account 'a' is required query parameter and can't be empty");
    }

    @Test
    public void validateBidIdShouldFailWhenBidIdIsMissing() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateBidId(routingContext))
                .withMessage("BidId 'b' is required query parameter and can't be empty");
    }

    @Test
    public void validateTimestampShouldFailWhenTimestampIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("ts", "invalid"));

        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateTimestamp(routingContext))
                .withMessage("Timestamp 'ts' query parameter is not valid number: invalid");
    }

    @Test
    public void validateFormatShouldFailWhenFormatIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("f", "invalid"));

        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateFormat(routingContext))
                .withMessage("Format 'f' query parameter is invalid. Possible values are b and i, but was invalid");
    }

    @Test
    public void validateFormatShouldSucceedWhenFormatIsBlank() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("f", "b"));

        // when and then
        assertThatCode(() -> EventUtil.validateFormat(routingContext))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateFormatShouldSucceedWhenFormatIsImage() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("f", "i"));

        // when and then
        assertThatCode(() -> EventUtil.validateFormat(routingContext))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateAnalyticsShouldFailWhenAnalyticsIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("x", "invalid"));

        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateAnalytics(routingContext))
                .withMessage("Analytics 'x' query parameter is invalid. Possible values are 1 and 0, but was invalid");
    }

    @Test
    public void validateAnalyticsShouldSucceedWhenAnalyticsIsDisabled() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("x", "0"));

        // when and then
        assertThatCode(() -> EventUtil.validateAnalytics(routingContext))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateAnalyticsShouldSucceedWhenAnalyticsIsEnabled() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("x", "1"));

        // when and then
        assertThatCode(() -> EventUtil.validateAnalytics(routingContext))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateIntegrationShouldFailWhenIntegrationHasInvalidSymbol() {
        // given
        given(httpRequest.getParam(eq("int"))).willReturn("invalid-_123*");

        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateIntegration(routingContext))
                .withMessage("Integration 'int' query parameter is not valid: invalid-_123*");
    }

    @Test
    public void validateIntegrationShouldFailWhenIntegrationIsTooLong() {
        // given
        given(httpRequest.getParam(eq("int"))).willReturn(
                "11111111112222222222333333333344444444445555555555666666666677777");

        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateIntegration(routingContext))
                .withMessage("Integration 'int' query parameter is longer 64 symbols: "
                        + "11111111112222222222333333333344444444445555555555666666666677777");
    }

    @Test
    public void validateIntegrationShouldSucceedForEmptyValue() {
        // given
        given(httpRequest.getParam(eq("int"))).willReturn("");

        // when and then
        assertThatCode(() -> EventUtil.validateIntegration(routingContext))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateIntegrationShouldSucceedForAlphaNumericUnderscoreAndDash() {
        // given
        given(httpRequest.getParam(eq("int"))).willReturn("int_-123");

        // when and then
        assertThatCode(() -> EventUtil.validateIntegration(routingContext))
                .doesNotThrowAnyException();
    }

    @Test
    public void fromShouldReturnExpectedEventRequest() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("a", "accountId")
                .add("bidder", "bidder")
                .add("b", "bidId")
                .add("ts", "1000")
                .add("f", "i")
                .add("x", "0")
                .add("l", "lineItemId"));

        // when
        final EventRequest result = EventUtil.from(routingContext);

        // then
        assertThat(result).isEqualTo(EventRequest.builder()
                .type(EventRequest.Type.win)
                .accountId("accountId")
                .bidder("bidder")
                .bidId("bidId")
                .timestamp(1000L)
                .format(EventRequest.Format.image)
                .analytics(EventRequest.Analytics.disabled)
                .lineItemId("lineItemId")
                .build());
    }

    @Test
    public void fromShouldReturnExpectedEventRequestWithDefaultFormatAndAnalytics() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("a", "accountId")
                .add("bidder", "bidder")
                .add("b", "bidId")
                .add("ts", "1000"));

        // when
        final EventRequest result = EventUtil.from(routingContext);

        // then
        assertThat(result).isEqualTo(EventRequest.builder()
                .type(EventRequest.Type.win)
                .accountId("accountId")
                .bidder("bidder")
                .bidId("bidId")
                .format(EventRequest.Format.blank)
                .analytics(EventRequest.Analytics.enabled)
                .timestamp(1000L)
                .build());
    }

    @Test
    public void toUrlShouldReturnExpectedUrl() {
        // given
        final EventRequest eventRequest = EventRequest.builder()
                .type(EventRequest.Type.win)
                .auctionId("auctionId")
                .accountId("accountId")
                .bidder("bidder")
                .bidId("bidId")
                .format(EventRequest.Format.blank)
                .integration("pbjs")
                .analytics(EventRequest.Analytics.enabled)
                .timestamp(1000L)
                .lineItemId("lineItemId")
                .build();

        // when
        final String result = EventUtil.toUrl("http://external-url", eventRequest);

        // then
        assertThat(result).isEqualTo(
                "http://external-url/event?t=win&b=bidId&a=accountId"
                        + "&aid=auctionId&ts=1000&bidder=bidder&f=b&int=pbjs&x=1"
                        + "&l=lineItemId");
    }

    @Test
    public void toUrlShouldReturnExpectedUrlWithoutFormatAndAnalyticsAndLineItemId() {
        // given
        final EventRequest eventRequest = EventRequest.builder()
                .type(EventRequest.Type.win)
                .auctionId("auctionId")
                .accountId("accountId")
                .bidder("bidder")
                .bidId("bidId")
                .timestamp(1000L)
                .build();

        // when
        final String result = EventUtil.toUrl("http://external-url", eventRequest);

        // then
        assertThat(result).isEqualTo("http://external-url/event?t=win&b=bidId&a=accountId"
                + "&aid=auctionId&ts=1000&bidder=bidder&int=");
    }

    @Test
    public void toUrlShouldReturnExpectedUrlWithoutTimestamp() {
        // given
        final EventRequest eventRequest = EventRequest.builder()
                .type(EventRequest.Type.win)
                .auctionId("auctionId")
                .accountId("accountId")
                .bidder("bidder")
                .bidId("bidId")
                .timestamp(null)
                .build();

        // when
        final String result = EventUtil.toUrl("http://external-url", eventRequest);

        // then
        assertThat(result).isEqualTo("http://external-url/event?t=win"
                + "&b=bidId&a=accountId&aid=auctionId&bidder=bidder&int=");
    }
}
