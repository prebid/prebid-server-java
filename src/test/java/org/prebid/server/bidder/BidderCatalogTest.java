package org.prebid.server.bidder;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.proto.response.BidderInfo;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class BidderCatalogTest {

    private static final String BIDDER = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Bidder bidder;

    private BidderCatalog bidderCatalog;

    @Test
    public void isValidNameShouldReturnTrueForKnownBidder() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .build()));
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.isValidName(BIDDER)).isTrue();
    }

    @Test
    public void isValidNameShouldReturnFalseForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.isValidName("unknown_bidder")).isFalse();
    }

    @Test
    public void isDeprecatedNameShouldReturnTrueForDeprecatedBidder() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(singletonList("deprecated"))
                .build()));
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.isDeprecatedName("deprecated")).isTrue();
    }

    @Test
    public void isDeprecatedNameShouldReturnFalseForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.isDeprecatedName("unknown_bidder")).isFalse();
    }

    @Test
    public void errorForDeprecatedNameShouldReturnErrorForDeprecatedBidder() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(singletonList("deprecated"))
                .build()));
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.errorForDeprecatedName("deprecated"))
                .isEqualTo("deprecated has been deprecated and is no longer available. Use rubicon instead.");
    }

    @Test
    public void metaInfoByNameShouldReturnMetaInfoForKnownBidder() {
        // given
        final BidderInfo bidderInfo = BidderInfo.create(
                true,
                null,
                null,
                "test@email.com",
                singletonList("banner"),
                singletonList("video"),
                null,
                99,
                true,
                false);

        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .bidderInfo(bidderInfo)
                .build()));

        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.bidderInfoByName(BIDDER)).isEqualTo(bidderInfo);
    }

    @Test
    public void metaInfoByNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.bidderInfoByName("unknown_bidder")).isNull();
    }

    @Test
    public void usersyncerByNameShouldReturnUsersyncerForKnownBidder() {
        // given
        final Usersyncer usersyncer = Usersyncer.of(null, null, null);
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .usersyncer(usersyncer)
                .build()));
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.usersyncerByName(BIDDER)).isEqualTo(usersyncer);
    }

    @Test
    public void usersyncerByNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.usersyncerByName("unknown_bidder")).isNull();
    }

    @Test
    public void bidderByNameShouldReturnBidderForKnownBidder() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .bidder(bidder)
                .build()));
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.bidderByName(BIDDER)).isSameAs(bidder);
    }

    @Test
    public void nameByVendorIdShouldReturnBidderNameForVendorId() {
        // given
        final BidderInfo bidderInfo = BidderInfo.create(
                true,
                null,
                null,
                "test@email.com",
                singletonList("banner"),
                singletonList("video"),
                null,
                99,
                true,
                false);

        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .bidder(bidder)
                .bidderInfo(bidderInfo)
                .build()));
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.nameByVendorId(99)).isEqualTo(BIDDER);
    }

    @Test
    public void bidderByNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.bidderByName("unknown_bidder")).isNull();
    }
}
