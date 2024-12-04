package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class CcpaEnforcementTest {

    @Mock
    private UserFpdCcpaMask userFpdCcpaMask;
    @Mock(strictness = LENIENT)
    private BidderCatalog bidderCatalog;
    @Mock
    private Metrics metrics;

    private CcpaEnforcement target;

    @Mock(strictness = LENIENT)
    private BidderAliases aliases;
    @Mock
    private ActivityInfrastructure activityInfrastructure;

    @BeforeEach
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
                        null,
                        true,
                        false,
                        null,
                        Ortb.of(false)));

        target = new CcpaEnforcement(userFpdCcpaMask, bidderCatalog, metrics, true);

        given(aliases.resolveBidder(anyString()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void enforceShouldNotModifyListWhenCcpaIsNotEnforced() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context
                .privacyContext(PrivacyContext.of(Privacy.builder().ccpa(Ccpa.of("1YN-")).build(), null, null)));
        final List<BidderPrivacyResult> initialResults = givenPrivacyResults(givenUser(), givenDevice());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, aliases, initialResults).result();

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(initialResults);
        verify(metrics).updatePrivacyCcpaMetrics(
                eq(activityInfrastructure),
                eq(true),
                eq(false),
                eq(true),
                eq(Collections.emptySet()));
    }

    @Test
    public void enforceShouldConsiderEnforceCcpaConfigurationProperty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context.account(Account.empty("id")));

        final List<BidderPrivacyResult> initialResults = givenPrivacyResults(givenUser(), givenDevice());

        target = new CcpaEnforcement(userFpdCcpaMask, bidderCatalog, metrics, false);

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, aliases, initialResults).result();

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(initialResults);
        verify(metrics).updatePrivacyCcpaMetrics(
                eq(activityInfrastructure),
                eq(true),
                eq(true),
                eq(false),
                eq(Collections.emptySet()));
    }

    @Test
    public void enforceShouldConsiderAccountCcpaEnabledProperty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context
                .account(Account.builder()
                        .privacy(AccountPrivacyConfig.builder()
                                .ccpa(AccountCcpaConfig.builder().enabled(false).build())
                                .build())
                        .build()));
        final List<BidderPrivacyResult> initialResults = givenPrivacyResults(givenUser(), givenDevice());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, aliases, initialResults).result();

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(initialResults);
        verify(metrics).updatePrivacyCcpaMetrics(
                eq(activityInfrastructure),
                eq(true),
                eq(true),
                eq(false),
                eq(Collections.emptySet()));
    }

    @Test
    public void enforceShouldConsiderAccountCcpaEnabledForRequestTypeProperty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context
                .requestTypeMetric(MetricName.openrtb2app));
        final List<BidderPrivacyResult> initialResults = givenPrivacyResults(givenUser(), givenDevice());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, aliases, initialResults).result();

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(initialResults);
        verify(metrics).updatePrivacyCcpaMetrics(
                eq(activityInfrastructure),
                eq(true),
                eq(true),
                eq(false),
                eq(Collections.emptySet()));
    }

    @Test
    public void enforceShouldTreatAllBiddersAsNoSale() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(context -> context
                .bidRequest(BidRequest.builder()
                        .device(givenDevice())
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .nosale(singletonList("*"))
                                .build()))
                        .build()));

        final List<BidderPrivacyResult> initialResults = List.of(
                BidderPrivacyResult.builder().requestBidder("bidder").user(givenUser()).device(givenDevice()).build(),
                BidderPrivacyResult.builder().requestBidder("noSale").user(givenUser()).device(givenDevice()).build());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, aliases, initialResults).result();

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(initialResults);
        verify(metrics).updatePrivacyCcpaMetrics(
                eq(activityInfrastructure),
                eq(true),
                eq(true),
                eq(true),
                eq(Collections.emptySet()));
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
                        null,
                        false,
                        false,
                        null,
                        Ortb.of(false)));

        final AuctionContext auctionContext = givenAuctionContext(identity());

        final List<BidderPrivacyResult> initialResults = List.of(
                BidderPrivacyResult.builder()
                        .requestBidder("bidderAlias")
                        .user(givenUser())
                        .device(givenDevice())
                        .build(),
                BidderPrivacyResult.builder()
                        .requestBidder("noSale")
                        .user(givenUser())
                        .device(givenDevice())
                        .build());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, aliases, initialResults).result();

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(initialResults);
        verify(metrics).updatePrivacyCcpaMetrics(
                eq(activityInfrastructure),
                eq(true),
                eq(true),
                eq(true),
                eq(Collections.emptySet()));
    }

    @Test
    public void enforceShouldReturnExpectedResult() {
        // given
        final User maskedUser = User.builder().id("maskedUser").build();
        final Device maskedDevice = Device.builder().ip("maskedDevice").build();

        given(userFpdCcpaMask.maskUser(any())).willReturn(maskedUser);
        given(userFpdCcpaMask.maskDevice(any())).willReturn(maskedDevice);

        final AuctionContext auctionContext = givenAuctionContext(identity());

        final List<BidderPrivacyResult> initialResults = List.of(
                BidderPrivacyResult.builder().requestBidder("bidder").user(givenUser()).device(givenDevice()).build(),
                BidderPrivacyResult.builder().requestBidder("noSale").user(givenUser()).device(givenDevice()).build());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, aliases, initialResults).result();

        // then
        assertThat(result).containsExactlyInAnyOrder(
                BidderPrivacyResult.builder().requestBidder("bidder").user(maskedUser).device(maskedDevice).build(),
                BidderPrivacyResult.builder().requestBidder("noSale").user(givenUser()).device(givenDevice()).build());

        verify(metrics).updatePrivacyCcpaMetrics(
                eq(activityInfrastructure),
                eq(true),
                eq(true),
                eq(true),
                eq(Set.of("bidder")));
    }

    private AuctionContext givenAuctionContext(
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer) {

        final AuctionContext.AuctionContextBuilder initialContext = AuctionContext.builder()
                .activityInfrastructure(activityInfrastructure)
                .bidRequest(BidRequest.builder()
                        .device(givenDevice())
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .nosale(singletonList("noSale"))
                                .build()))
                        .build())
                .requestTypeMetric(MetricName.openrtb2web)
                .account(Account.builder()
                        .privacy(AccountPrivacyConfig.builder()
                                .ccpa(AccountCcpaConfig.builder()
                                        .enabled(true)
                                        .enabledForRequestType(EnabledForRequestType.of(
                                                true,
                                                false,
                                                false,
                                                false,
                                                false))
                                        .build())
                                .build())
                        .build())
                .privacyContext(PrivacyContext.of(Privacy.builder().ccpa(Ccpa.of("1YY-")).build(), null, null));

        return auctionContextCustomizer.apply(initialContext).build();
    }

    private static List<BidderPrivacyResult> givenPrivacyResults(User user, Device device) {
        return singletonList(BidderPrivacyResult.builder().requestBidder("bidder").user(user).device(device).build());
    }

    private static User givenUser() {
        return User.builder().id("originalUser").build();
    }

    private static Device givenDevice() {
        return Device.builder().ip("originalDevice").build();
    }
}
