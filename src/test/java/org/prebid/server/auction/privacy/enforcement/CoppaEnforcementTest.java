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
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdCoppaMask;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class CoppaEnforcementTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UserFpdCoppaMask userFpdCoppaMask;
    @Mock
    private Metrics metrics;

    private CoppaEnforcement target;

    @Before
    public void setUp() {
        target = new CoppaEnforcement(userFpdCoppaMask, metrics);
    }

    @Test
    public void isApplicableShouldReturnFalse() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .privacyContext(PrivacyContext.of(Privacy.builder().coppa(0).build(), null, null))
                .build();

        // when and then
        assertThat(target.isApplicable(auctionContext)).isFalse();
    }

    @Test
    public void isApplicableShouldReturnTrue() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .privacyContext(PrivacyContext.of(Privacy.builder().coppa(1).build(), null, null))
                .build();

        // when and then
        assertThat(target.isApplicable(auctionContext)).isTrue();
    }

    @Test
    public void enforceShouldReturnExpectedResultAndEmitMetrics() {
        // given
        final User maskedUser = User.builder().id("maskedUser").build();
        final Device maskedDevice = Device.builder().ip("maskedDevice").build();

        given(userFpdCoppaMask.maskUser(any())).willReturn(maskedUser);
        given(userFpdCoppaMask.maskDevice(any())).willReturn(maskedDevice);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().ip("originalDevice").build()).build())
                .build();
        final Map<String, User> bidderToUser = Map.of("bidder", User.builder().id("originalUser").build());

        // when
        final List<BidderPrivacyResult> result = target.enforce(auctionContext, bidderToUser).result();

        // then
        assertThat(result).allSatisfy(privacyResult -> {
            assertThat(privacyResult.getUser()).isSameAs(maskedUser);
            assertThat(privacyResult.getDevice()).isSameAs(maskedDevice);
        });
        verify(metrics).updatePrivacyCoppaMetric();
    }
}
