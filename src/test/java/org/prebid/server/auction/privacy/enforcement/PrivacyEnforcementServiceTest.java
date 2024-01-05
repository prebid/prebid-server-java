package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.apache.commons.collections4.ListUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.BidderPrivacyResult;

import java.util.List;
import java.util.Map;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections.SetUtils.isEqualSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class PrivacyEnforcementServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CoppaEnforcement coppaEnforcement;
    @Mock
    private CcpaEnforcement ccpaEnforcement;
    @Mock
    private TcfEnforcement tcfEnforcement;
    @Mock
    private ActivityEnforcement activityEnforcement;

    private PrivacyEnforcementService target;

    @Before
    public void setUp() {
        target = new PrivacyEnforcementService(
                coppaEnforcement,
                ccpaEnforcement,
                tcfEnforcement,
                activityEnforcement);
    }

    @Test
    public void maskShouldUseCoppaEnforcementIfApplicable() {
        // given
        given(coppaEnforcement.isApplicable(any())).willReturn(true);

        final List<BidderPrivacyResult> bidderPrivacyResults = singletonList(null);
        given(coppaEnforcement.enforce(any(), any())).willReturn(Future.succeededFuture(bidderPrivacyResults));

        // when
        final List<BidderPrivacyResult> result = target.mask(null, null, null).result();

        // then
        assertThat(result).isSameAs(bidderPrivacyResults);
        verifyNoInteractions(ccpaEnforcement);
        verifyNoInteractions(tcfEnforcement);
        verifyNoInteractions(activityEnforcement);
    }

    @Test
    public void maskShouldReturnExpectedResult() {
        // given
        given(coppaEnforcement.isApplicable(any())).willReturn(false);

        given(ccpaEnforcement.enforce(any(), any(), any())).willReturn(Future.succeededFuture(
                singletonList(BidderPrivacyResult.builder().requestBidder("bidder1").build())));

        given(tcfEnforcement.enforce(
                any(),
                any(),
                argThat(bidders -> isEqualSet(bidders, singleton("bidder0"))),
                any()))
                .willReturn(Future.succeededFuture(
                        singletonList(BidderPrivacyResult.builder().requestBidder("bidder0").build())));

        given(activityEnforcement.enforce(any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(ListUtils.union(
                        invocation.getArgument(0),
                        singletonList(BidderPrivacyResult.builder().requestBidder("bidder2").build()))));

        final Map<String, User> bidderToUser = Map.of(
                "bidder0", User.builder().build(),
                "bidder1", User.builder().build());

        // when
        final List<BidderPrivacyResult> result = target.mask(null, bidderToUser, null).result();

        // then
        assertThat(result).containsExactly(
                BidderPrivacyResult.builder().requestBidder("bidder1").build(),
                BidderPrivacyResult.builder().requestBidder("bidder0").build(),
                BidderPrivacyResult.builder().requestBidder("bidder2").build());
        verify(coppaEnforcement, times(0)).enforce(any(), any());
    }
}
