package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdCcpaMask;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCcpaConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;
import org.prebid.server.spring.config.bidder.model.Ortb;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class CcpaEnforcementTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UserFpdCcpaMask userFpdCcpaMask;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private Metrics metrics;

    private CcpaEnforcement target;

    @Mock
    private BidderAliases aliases;

    @Before
    public void setUp() {
        given(bidderCatalog.bidderInfoByName("bidder"))
                .willReturn(BidderInfo.create(
                        true,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        true,
                        false,
                        null,
                        Ortb.of(false)));

        target = new CcpaEnforcement(userFpdCcpaMask, bidderCatalog, metrics, true);

        given(aliases.resolveBidder(anyString()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void enforceShouldReturnEmptyListWhenCcpaNotEnforced() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context
                .privacyContext(PrivacyContext.of(Privacy.builder().ccpa(Ccpa.of("1YN-")).build(), null, null)));

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, null, aliases).result();

        // then
        assertThat(result).isEmpty();
        verify(metrics).updatePrivacyCcpaMetrics(eq(true), eq(false));
    }

    @Test
    public void enforceShouldConsiderEnforceCcpaConfigurationProperty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context.account(Account.empty("id")));

        target = new CcpaEnforcement(userFpdCcpaMask, bidderCatalog, metrics, false);

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, null, aliases).result();

        // then
        assertThat(result).isEmpty();
        verify(metrics).updatePrivacyCcpaMetrics(eq(true), eq(true));
    }

    @Test
    public void enforceShouldConsiderAccountCcpaEnabledProperty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context
                .account(Account.builder()
                        .privacy(AccountPrivacyConfig.of(
                                null,
                                AccountCcpaConfig.builder().enabled(false).build(),
                                null,
                                null))
                        .build()));

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, null, aliases).result();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void enforceShouldConsiderAccountCcpaEnabledForRequestTypeProperty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context
                .requestTypeMetric(MetricName.openrtb2app));

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, null, aliases).result();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void enforceShouldTreatAllBiddersAsNoSale() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context
                .bidRequest(BidRequest.builder()
                        .device(Device.builder().ip("originalDevice").build())
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .nosale(singletonList("*"))
                                .build()))
                        .build()));

        final Map<String, User> bidderToUser = Map.of(
                "bidder", User.builder().id("originalUser").build(),
                "noSale", User.builder().id("originalUser").build());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, bidderToUser, aliases).result();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void enforceShouldSkipNoSaleBiddersAndNotEnforcedByBidderConfig() {
        // given
        given(aliases.resolveBidder("bidderAlias")).willReturn("bidder");
        given(bidderCatalog.bidderInfoByName("bidder"))
                .willReturn(BidderInfo.create(
                        true,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        false,
                        false,
                        null,
                        Ortb.of(false)));

        final AuctionContext auctionContext = givenAuctionContext(identity());

        final Map<String, User> bidderToUser = Map.of(
                "bidderAlias", User.builder().id("originalUser").build(),
                "noSale", User.builder().id("originalUser").build());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, bidderToUser, aliases).result();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void enforceShouldReturnExpectedResult() {
        // given
        final User maskedUser = User.builder().id("maskedUser").build();
        final Device maskedDevice = Device.builder().ip("maskedDevice").build();

        given(userFpdCcpaMask.maskUser(any())).willReturn(maskedUser);
        given(userFpdCcpaMask.maskDevice(any())).willReturn(maskedDevice);

        final AuctionContext auctionContext = givenAuctionContext(identity());

        final Map<String, User> bidderToUser = Map.of(
                "bidder", User.builder().id("originalUser").build(),
                "noSale", User.builder().id("originalUser").build());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, bidderToUser, aliases).result();

        // then
        assertThat(result)
                .hasSize(1)
                .allSatisfy(privacyResult -> {
                    assertThat(privacyResult.getUser()).isSameAs(maskedUser);
                    assertThat(privacyResult.getDevice()).isSameAs(maskedDevice);
                });
    }

    private static AuctionContext givenAuctionContext(
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer) {

        final AuctionContext.AuctionContextBuilder initialContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .device(Device.builder().ip("originalDevice").build())
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .nosale(singletonList("noSale"))
                                .build()))
                        .build())
                .requestTypeMetric(MetricName.openrtb2web)
                .account(Account.builder()
                        .privacy(AccountPrivacyConfig.of(
                                null,
                                AccountCcpaConfig.builder()
                                        .enabled(true)
                                        .enabledForRequestType(EnabledForRequestType.of(
                                                true,
                                                false,
                                                false,
                                                false,
                                                false))
                                        .build(),
                                null,
                                null))
                        .build())
                .privacyContext(PrivacyContext.of(Privacy.builder().ccpa(Ccpa.of("1YY-")).build(), null, null));

        return auctionContextCustomizer.apply(initialContext).build();
    }
}
