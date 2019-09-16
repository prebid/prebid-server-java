package org.prebid.server.events;

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
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
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
    }

    @Test
    public void validateTypeShouldFailWhenTypeIsMissing() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());

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
    public void validateBidIdShouldFailWhenBidIdIsMissing() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win"));

        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateBidId(routingContext))
                .withMessage("BidId 'b' is required query parameter and can't be empty");
    }

    @Test
    public void validateAccountIdShouldFailWhenAccountIsMissing() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("b", "bidId"));

        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> EventUtil.validateAccountId(routingContext))
                .withMessage("Account 'a' is required query parameter and can't be empty");
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
    public void fromShouldReturnExpectedEventRequest() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("f", "i")
                .add("x", "0"));

        // when
        final EventRequest result = EventUtil.from(routingContext);

        // then
        assertThat(result).isEqualTo(EventRequest.builder()
                .type(EventRequest.Type.win)
                .bidId("bidId")
                .accountId("accountId")
                .format(EventRequest.Format.image)
                .analytics(EventRequest.Analytics.disabled)
                .build());
    }

    @Test
    public void fromShouldReturnExpectedEventRequestWithDefaultFormatAndAnalytics() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId"));

        // when
        final EventRequest result = EventUtil.from(routingContext);

        // then
        assertThat(result).isEqualTo(EventRequest.builder()
                .type(EventRequest.Type.win)
                .bidId("bidId")
                .accountId("accountId")
                .format(EventRequest.Format.blank)
                .analytics(EventRequest.Analytics.enabled)
                .build());
    }

    @Test
    public void toUrlShouldReturnExpectedUrl() {
        // given
        final EventRequest eventRequest = EventRequest.builder()
                .type(EventRequest.Type.win)
                .bidId("bidId")
                .accountId("accountId")
                .format(EventRequest.Format.blank)
                .analytics(EventRequest.Analytics.enabled)
                .build();

        // when
        final String result = EventUtil.toUrl("http://external-url", eventRequest);

        // then
        assertThat(result).isEqualTo("http://external-url/event?t=win&b=bidId&a=accountId&f=b&x=1");
    }

    @Test
    public void toUrlShouldReturnExpectedUrlWithoutFormatAndAnalytics() {
        // given
        final EventRequest eventRequest = EventRequest.builder()
                .type(EventRequest.Type.win)
                .bidId("bidId")
                .accountId("accountId")
                .build();

        // when
        final String result = EventUtil.toUrl("http://external-url", eventRequest);

        // then
        assertThat(result).isEqualTo("http://external-url/event?t=win&b=bidId&a=accountId");
    }
}
