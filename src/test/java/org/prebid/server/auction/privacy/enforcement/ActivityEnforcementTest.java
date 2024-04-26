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
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class ActivityEnforcementTest {

    @Mock
    private UserFpdActivityMask userFpdActivityMask;

    private ActivityEnforcement target;

    @Mock
    private ActivityInfrastructure activityInfrastructure;

    @BeforeEach
    public void setUp() {
        target = new ActivityEnforcement(userFpdActivityMask);
    }

    @Test
    public void enforceShouldReturnExpectedResult() {
        // given
        final User maskedUser = User.builder().id("maskedUser").build();
        final Device maskedDevice = Device.builder().ip("maskedDevice").build();

        given(activityInfrastructure.isAllowed(any(), any())).willReturn(false);
        given(userFpdActivityMask.maskUser(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .willReturn(maskedUser);
        given(userFpdActivityMask.maskDevice(any(), anyBoolean(), anyBoolean()))
                .willReturn(maskedDevice);

        final BidderPrivacyResult bidderPrivacyResult = BidderPrivacyResult.builder()
                .requestBidder("bidder")
                .user(User.builder().id("originalUser").build())
                .device(Device.builder().ip("originalDevice").build())
                .build();

        final AuctionContext context = givenAuctionContext();

        // when
        final List<BidderPrivacyResult> result = target.enforce(singletonList(bidderPrivacyResult), context).result();

        //then
        assertThat(result).allSatisfy(privacyResult -> {
            assertThat(privacyResult.getUser()).isSameAs(maskedUser);
            assertThat(privacyResult.getDevice()).isSameAs(maskedDevice);
        });
    }

    private AuctionContext givenAuctionContext() {
        return AuctionContext.builder()
                .bidRequest(BidRequest.builder().build())
                .activityInfrastructure(activityInfrastructure)
                .build();
    }
}
