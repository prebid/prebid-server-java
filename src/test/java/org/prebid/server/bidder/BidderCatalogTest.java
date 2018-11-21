package org.prebid.server.bidder;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class BidderCatalogTest {

    private static final String BIDDER = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MetaInfo metaInfo;
    @Mock
    private Usersyncer usersyncer;
    @Mock
    private Bidder bidder;
    @Mock
    private Adapter adapter;
    @Mock
    private BidderRequester bidderRequester;

    private BidderDeps bidderDeps;
    private BidderCatalog bidderCatalog;

    @Test
    public void isValidNameShouldReturnTrueForKnownBidder() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), emptyList(), null, null, null, null, null);
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
        bidderDeps = BidderDeps.of(BIDDER, singletonList("deprecated"), emptyList(), null, null, null, null, null);
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
        bidderDeps = BidderDeps.of(BIDDER, singletonList("deprecated"), emptyList(), null, null, null, null, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.errorForDeprecatedName("deprecated"))
                .isEqualTo("deprecated has been deprecated and is no longer available. Use rubicon instead.");
    }

    @Test
    public void isAliasShouldReturnTrueForBidderAlias() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), singletonList("alias"), null, null, null, null, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.isAlias("alias")).isTrue();
    }

    @Test
    public void isAliasShouldReturnFalseForUnknownBidderAlias() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.isAlias("alias")).isFalse();
    }

    @Test
    public void nameByAliasShouldReturnBidderName() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), singletonList("alias"), null, null, null, null, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.nameByAlias("alias")).isEqualTo(BIDDER);
    }

    @Test
    public void metaInfoByNameShouldReturnMetaInfoForKnownBidder() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), emptyList(), metaInfo, null, null, null, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.metaInfoByName(BIDDER)).isEqualTo(metaInfo);
    }

    @Test
    public void metaInfoByNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.metaInfoByName("unknown_bidder")).isNull();
    }

    @Test
    public void usersyncerByNameShouldReturnUsersyncerForKnownBidder() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), emptyList(), null, usersyncer, null, null, null);
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
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), emptyList(), null, null, bidder, null, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.bidderByName(BIDDER)).isSameAs(bidder);
    }

    @Test
    public void bidderByNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.bidderByName("unknown_bidder")).isNull();
    }

    @Test
    public void adapterByNameShouldReturnAdapterForKnownBidder() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), emptyList(), null, null, null, adapter, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.adapterByName(BIDDER)).isSameAs(adapter);
    }

    @Test
    public void adapterByNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.adapterByName("unknown_bidder")).isNull();
    }

    @Test
    public void bidderRequesterByNameShouldReturnBidderRequesterForKnownBidder() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), emptyList(), null, null, null, null, bidderRequester);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.bidderRequesterByName(BIDDER)).isEqualTo(bidderRequester);
    }

    @Test
    public void bidderRequesterByNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.bidderRequesterByName("unknown_bidder")).isNull();
    }

    @Test
    public void isValidAdapterNameShouldReturnTrueIfNameIsValidAndAdapterIsDefined() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), emptyList(), null, null, null, adapter, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.isValidAdapterName(BIDDER)).isTrue();
    }

    @Test
    public void isValidAdapterNameShouldReturnFalseIfNameIsInvalid() {
        // given
        bidderDeps = BidderDeps.of("invalid", emptyList(), emptyList(), null, null, null, adapter, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.isValidAdapterName(BIDDER)).isFalse();
    }

    @Test
    public void isValidAdapterNameShouldReturnFalseIfAdapterIsNotDefined() {
        // given
        bidderDeps = BidderDeps.of(BIDDER, emptyList(), emptyList(), null, null, null, null, null);
        bidderCatalog = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(bidderCatalog.isValidAdapterName(BIDDER)).isFalse();
    }
}
