package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;

import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

@ExtendWith(MockitoExtension.class)
public class PrivacyEnforcementServiceTest {

    @Mock
    private PrivacyEnforcement firstEnforcement;

    @Mock
    private PrivacyEnforcement secondEnforcement;

    @Mock
    private BidderAliases bidderAliases;

    @Test
    public void maskShouldPassBidderPrivacyThroughAllEnforcements() {
        // given
        final BidderPrivacyResult expectedResult = BidderPrivacyResult.builder()
                .requestBidder("bidder")
                .user(User.EMPTY)
                .device(Device.builder().build())
                .build();

        given(firstEnforcement.enforce(any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonList(expectedResult)));
        given(secondEnforcement.enforce(any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonList(expectedResult)));

        final PrivacyEnforcementService target = new PrivacyEnforcementService(
                List.of(firstEnforcement, secondEnforcement));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().build()).build())
                .build();

        final User user = User.builder().id("originalUser").build();
        final Map<String, User> bidderToUser = singletonMap("bidder", user);

        // when
        final Future<List<BidderPrivacyResult>> result = target.mask(auctionContext, bidderToUser, bidderAliases);

        // then
        assertThat(result)
                .isSucceeded()
                .unwrap()
                .asList()
                .containsExactlyInAnyOrder(expectedResult);
    }
}
