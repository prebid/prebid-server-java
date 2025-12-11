package org.prebid.server.hooks.modules.id5.userid.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidRequestUtilsTest {

    @Test
    void shouldReturnFalseWhenUserIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when & then
        assertThat(BidRequestUtils.isId5IdPresent(bidRequest)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenEidsIsNull() {
        // given
        final User user = User.builder().build(); // eids null
        final BidRequest bidRequest = BidRequest.builder().user(user).build();

        // when & then
        assertThat(BidRequestUtils.isId5IdPresent(bidRequest)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenOnlyOtherSourcesPresent() {
        // given
        final User user = User.builder()
                .eids(List.of(
                        Eid.builder().source("other-source").uids(List.of(Uid.builder().id("x").build())).build()))
                .build();
        final BidRequest bidRequest = BidRequest.builder().user(user).build();

        // when & then
        assertThat(BidRequestUtils.isId5IdPresent(bidRequest)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenId5SourcePresentAmongOthers() {
        // given
        final User user = User.builder()
                .eids(List.of(
                        Eid.builder().source("other-source").uids(List.of(Uid.builder().id("x").build())).build(),
                        Eid.builder().source(BidRequestUtils.ID5_ID_SOURCE)
                                .uids(List.of(Uid.builder().id("id5-1").build())).build()))
                .build();
        final BidRequest bidRequest = BidRequest.builder().user(user).build();

        // when & then
        assertThat(BidRequestUtils.isId5IdPresent(bidRequest)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenEidSourceIsNull() {
        // given
        final User user = User.builder()
                .eids(List.of(Eid.builder().source(null).uids(List.of(Uid.builder().id("x").build())).build()))
                .build();
        final BidRequest bidRequest = BidRequest.builder().user(user).build();

        // when & then
        assertThat(BidRequestUtils.isId5IdPresent(bidRequest)).isFalse();
    }
}
