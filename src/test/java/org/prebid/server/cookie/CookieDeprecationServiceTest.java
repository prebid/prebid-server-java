package org.prebid.server.cookie;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.model.PartitionedCookie;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPrivacySandboxConfig;
import org.prebid.server.settings.model.AccountPrivacySandboxCookieDeprecationConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class CookieDeprecationServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    private RoutingContext routingContext;

    private final CookieDeprecationService target = new CookieDeprecationService();

    @Before
    public void before() {
        given(routingContext.cookieMap()).willReturn(Map.of());
    }

    @Test
    public void makeCookieShouldReturnNullWhenRequestContainsDeprecationCookie() {
        // given
        given(routingContext.cookieMap())
                .willReturn(Map.of("receive-cookie-deprecation", Cookie.cookie("receive-cookie-deprecation", "1")));

        // when
        final PartitionedCookie actualCookie = target.makeCookie(
                Account.builder().id("accountId").auction(AccountAuctionConfig.builder().build()).build(),
                routingContext);

        // then
        assertThat(actualCookie).isNull();
    }

    @Test
    public void makeCookieShouldReturnNullWhenRequestContainsDeprecationCookieAndAccountIsEmpty() {
        // given
        given(routingContext.cookieMap())
                .willReturn(Map.of("receive-cookie-deprecation", Cookie.cookie("receive-cookie-deprecation", "1")));

        // when
        final PartitionedCookie actualCookie = target.makeCookie(Account.builder().build(), routingContext);

        // then
        assertThat(actualCookie).isNull();
    }

    @Test
    public void makeCookieShouldReturnNullWhenCookieDeprecationIsNotConfiguredInAccount() {
        // given
        final Account givenAccount = givenAccount(null, null);

        // when
        final PartitionedCookie actualCookie = target.makeCookie(givenAccount, routingContext);

        // then
        assertThat(actualCookie).isNull();
    }

    @Test
    public void makeCookieShouldReturnNullWhenCookieDeprecationIsNotEnabledInAccount() {
        // given
        final Account givenAccount = givenAccount(false, 100L);

        // when
        final PartitionedCookie actualCookie = target.makeCookie(givenAccount, routingContext);

        // then
        assertThat(actualCookie).isNull();
    }

    @Test
    public void makeCookieShouldReturnCookieWithDefaultMaxAgeWhenCookieDeprecationEnabledAndTtlIsNotSetInAccount() {
        // given
        final Account givenAccount = givenAccount(true, null);

        // when
        final PartitionedCookie actualCookie = target.makeCookie(givenAccount, routingContext);

        // then
        final PartitionedCookie expectedCookie = PartitionedCookie.of(
                Cookie.cookie("receive-cookie-deprecation", "1")
                        .setPath("/")
                        .setSameSite(CookieSameSite.NONE)
                        .setSecure(true)
                        .setHttpOnly(true)
                        .setMaxAge(604800L));

        assertThat(actualCookie).usingRecursiveComparison().isEqualTo(expectedCookie);
    }

    @Test
    public void makeCookieShouldReturnCookieWhenCookieDeprecationIsEnabledAndTtlIsConfiguredInAccount() {
        // given
        final Account givenAccount = givenAccount(true, 100L);

        // when
        final PartitionedCookie actualCookie = target.makeCookie(givenAccount, routingContext);

        // then
        final PartitionedCookie expectedCookie = PartitionedCookie.of(
                Cookie.cookie("receive-cookie-deprecation", "1")
                        .setPath("/")
                        .setSameSite(CookieSameSite.NONE)
                        .setSecure(true)
                        .setHttpOnly(true)
                        .setMaxAge(100L));

        assertThat(actualCookie).usingRecursiveComparison().isEqualTo(expectedCookie);
    }

    @Test
    public void updateBidRequestDeviceShouldNotChangeRequestWhenSecCookieDeprecationIsAbsent() {
        // given
        final Map<String, String> headers = Map.of("header", "value");
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = givenAccount(true, 100L);
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        assertThat(actualBidRequest).isEqualTo(bidRequest);
        assertThat(debugWarnings).isEmpty();
    }

    @Test
    public void updateBidRequestDeviceShouldNotChangeRequestAndDebugWarnWhenSecCookieDeprecationIsPresentButInvalid() {
        // given
        final Map<String, String> headers = Map.of(
                "header", "value",
                "sec-cookie-deprecation", RandomStringUtils.random(101));
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = givenAccount(true, 100L);
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        assertThat(actualBidRequest).isEqualTo(bidRequest);
        assertThat(debugWarnings).hasSize(1).first()
                .isEqualTo("Sec-Cookie-Deprecation header has invalid value");
    }

    @Test
    public void updateBidRequestDeviceShouldNotChangeRequestWhenDeviceExtCdepIsPresent() {
        // given
        final Map<String, String> headers = Map.of(
                "header", "value",
                "sec-cookie-deprecation", RandomStringUtils.random(100));
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = givenAccount(true, 100L);
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final BidRequest bidRequest = givenBidRequest("cdep value", identity());

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        assertThat(actualBidRequest).isEqualTo(bidRequest);
        assertThat(debugWarnings).isEmpty();
    }

    @Test
    public void updateBidRequestDeviceShouldNotChangeRequestWhenAccountIsEmpty() {
        // given
        final Map<String, String> headers = Map.of(
                "header", "value",
                "sec-cookie-deprecation", RandomStringUtils.random(100));
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = Account.empty("accountId");
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        assertThat(actualBidRequest).isEqualTo(bidRequest);
        assertThat(debugWarnings).isEmpty();
    }

    @Test
    public void updateBidRequestDeviceShouldNotChangeRequestWhenCookieDeprecationIsNotConfiguredInAccount() {
        // given
        final Map<String, String> headers = Map.of(
                "header", "value",
                "sec-cookie-deprecation", RandomStringUtils.random(100));
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = givenAccount(null, null);
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        assertThat(actualBidRequest).isEqualTo(bidRequest);
        assertThat(debugWarnings).isEmpty();
    }

    @Test
    public void updateBidRequestDeviceShouldNotChangeRequestWhenCookieDeprecationIsDisabledInAccount() {
        // given
        final Map<String, String> headers = Map.of(
                "header", "value",
                "sec-cookie-deprecation", RandomStringUtils.random(100));
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = givenAccount(false, 100L);
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        assertThat(actualBidRequest).isEqualTo(bidRequest);
        assertThat(debugWarnings).isEmpty();
    }

    @Test
    public void updateBidRequestDeviceShouldAddCdepValueWhenDeviceExtIsAbsent() {
        // given
        final String cdep = RandomStringUtils.random(100);
        final Map<String, String> headers = Map.of(
                "header", "value",
                "sec-cookie-deprecation", cdep);
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = givenAccount(true, 100L);
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(null).ip("ip"));

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        assertThat(actualBidRequest).isEqualTo(givenBidRequest(cdep, builder -> builder.ip("ip")));
        assertThat(debugWarnings).isEmpty();
    }

    @Test
    public void updateBidRequestDeviceShouldAddCdepValueWhenDeviceIsAbsent() {
        // given
        final String cdep = RandomStringUtils.random(100);
        final Map<String, String> headers = Map.of(
                "header", "value",
                "sec-cookie-deprecation", cdep);
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = givenAccount(true, 100L);
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final BidRequest bidRequest = BidRequest.builder().device(null).build();

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        assertThat(actualBidRequest).isEqualTo(givenBidRequest(cdep, identity()));
        assertThat(debugWarnings).isEmpty();
    }

    @Test
    public void updateBidRequestDeviceShouldAddCdepValueWhenDeviceExtIsPresentButWithoutCdep() {
        // given
        final String cdep = RandomStringUtils.random(100);
        final Map<String, String> headers = Map.of(
                "header", "value",
                "sec-cookie-deprecation", cdep);
        final List<String> debugWarnings = new ArrayList<>();
        final Account givenAccount = givenAccount(true, 100L);
        final AuctionContext auctionContext = givenContext(headers, givenAccount, debugWarnings);
        final ExtDevice extDevice = ExtDevice.of(1, ExtDevicePrebid.of(ExtDeviceInt.of(2, 3)));
        extDevice.addProperty("some_property", TextNode.valueOf("some_property_value"));
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(extDevice).ip("ip"));

        // when
        final BidRequest actualBidRequest = target.updateBidRequestDevice(bidRequest, auctionContext);

        // then
        final ExtDevice expectedExtDevice = ExtDevice.of(1, ExtDevicePrebid.of(ExtDeviceInt.of(2, 3)));
        expectedExtDevice.addProperty("cdep", TextNode.valueOf(cdep));
        expectedExtDevice.addProperty("some_property", TextNode.valueOf("some_property_value"));

        assertThat(actualBidRequest).isEqualTo(givenBidRequest(builder -> builder.ext(expectedExtDevice).ip("ip")));
        assertThat(debugWarnings).isEmpty();
    }

    private static Account givenAccount(Boolean enabled, Long ttlSec) {
        return Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .privacySandbox(AccountPrivacySandboxConfig.of(
                                AccountPrivacySandboxCookieDeprecationConfig.of(enabled, ttlSec)))
                        .build())
                .build();
    }

    private static AuctionContext givenContext(Map<String, String> headers, Account account, List<String> warnings) {
        final CaseInsensitiveMultiMap headersMap = CaseInsensitiveMultiMap.builder().addAll(headers).build();
        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().headers(headersMap).build())
                .debugWarnings(warnings)
                .account(account)
                .build();
    }

    private static BidRequest givenBidRequest(String cdep, UnaryOperator<Device.DeviceBuilder> deviceBuilder) {
        final ExtDevice extDevice = ExtDevice.empty();
        extDevice.addProperty("cdep", TextNode.valueOf(cdep));
        return BidRequest.builder()
                .device(deviceBuilder.apply(Device.builder().ext(extDevice)).build())
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Device.DeviceBuilder> deviceBuilder) {
        return BidRequest.builder()
                .device(deviceBuilder.apply(Device.builder()).build())
                .build();
    }

}
