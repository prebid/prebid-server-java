package org.prebid.server.bidder;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class BidderCatalogTest {

    private static final String BIDDER = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Bidder<?> bidder;

    private BidderCatalog target;

    @Test
    public void isValidNameShouldReturnTrueForKnownBidder() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.isValidName(BIDDER)).isTrue();
    }

    @Test
    public void isValidNameShouldReturnFalseForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.isValidName("unknown_bidder")).isFalse();
    }

    @Test
    public void isDeprecatedNameShouldReturnTrueForDeprecatedBidder() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(singletonList("deprecated"))
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.isDeprecatedName("deprecated")).isTrue();
    }

    @Test
    public void isDeprecatedNameShouldReturnFalseForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.isDeprecatedName("unknown_bidder")).isFalse();
    }

    @Test
    public void errorForDeprecatedNameShouldReturnErrorForDeprecatedBidder() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(singletonList("deprecated"))
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.errorForDeprecatedName("deprecated"))
                .isEqualTo("deprecated has been deprecated and is no longer available. Use rubicon instead.");
    }

    @Test
    public void metaInfoByNameShouldReturnMetaInfoForKnownBidder() {
        // given
        final BidderInfo bidderInfo = BidderInfo.create(
                true,
                null,
                true,
                null,
                null,
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                null,
                99,
                true,
                false,
                CompressionType.NONE);

        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .bidderInfo(bidderInfo)
                .build()));

        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.bidderInfoByName(BIDDER)).isEqualTo(bidderInfo);
    }

    @Test
    public void isAliasShouldReturnTrueForAlias() {
        // given
        final BidderInfo bidderInfo = BidderInfo.create(
                true,
                null,
                true,
                null,
                null,
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                null,
                99,
                true,
                false,
                CompressionType.NONE);

        final BidderInstanceDeps bidderInstanceDeps = BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .bidderInfo(bidderInfo)
                .build();

        final BidderInfo aliasInfo = BidderInfo.create(
                true,
                null,
                true,
                null,
                BIDDER,
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                null,
                99,
                true,
                false,
                CompressionType.NONE);

        final BidderInstanceDeps aliasInstanceDeps = BidderInstanceDeps.builder()
                .name("alias")
                .deprecatedNames(emptyList())
                .bidderInfo(aliasInfo)
                .build();

        final BidderDeps bidderDeps = BidderDeps.of(List.of(bidderInstanceDeps, aliasInstanceDeps));

        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.isAlias("alias")).isTrue();
    }

    @Test
    public void metaInfoByNameShouldReturnNullForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.bidderInfoByName("unknown_bidder")).isNull();
    }

    @Test
    public void usersyncerByNameShouldReturnUsersyncerForKnownBidder() {
        // given
        final Usersyncer usersyncer = Usersyncer.of("name", null, null);
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .usersyncer(usersyncer)
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.usersyncerByName(BIDDER)).contains(usersyncer);
    }

    @Test
    public void cookieFamilyNameShouldReturnCookieFamilyForKnownBidder() {
        // given
        final Usersyncer usersyncer = Usersyncer.of("name", null, null);
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .usersyncer(usersyncer)
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.cookieFamilyName(BIDDER)).contains("name");
    }

    @Test
    public void usersyncReadyBiddersShouldReturnBiddersThatCanSync() {
        // given
        final BidderInfo infoOfBidderWithUsersyncConfig = BidderInfo.create(
                true,
                null,
                true,
                null,
                "bidder-with-usersync",
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                null,
                99,
                true,
                false,
                CompressionType.NONE);

        final BidderInfo infoOfBidderWithoutUsersyncConfig = BidderInfo.create(
                true,
                null,
                true,
                null,
                "bidder-without-usersync",
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                null,
                99,
                true,
                false,
                CompressionType.NONE);

        final BidderInfo infoOfDisabledBidderWithUsersyncConfig = BidderInfo.create(
                false,
                null,
                true,
                null,
                "bidder-with-usersync",
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                null,
                99,
                true,
                false,
                CompressionType.NONE);

        final List<BidderDeps> bidderDeps = List.of(
                BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                        .name("bidder-with-usersync")
                        .deprecatedNames(emptyList())
                        .bidderInfo(infoOfBidderWithUsersyncConfig)
                        .usersyncer(Usersyncer.of("bidder-with-usersync-family", null, null))
                        .build())),
                BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                        .name("bidder-without-usersync")
                        .bidderInfo(infoOfBidderWithoutUsersyncConfig)
                        .deprecatedNames(emptyList())
                        .build())),
                BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                        .name("disabled-bidder-with-usersync")
                        .bidderInfo(infoOfDisabledBidderWithUsersyncConfig)
                        .usersyncer(Usersyncer.of("isabled-bidder-with-usersync-family", null, null))
                        .deprecatedNames(emptyList())
                        .build())));

        target = new BidderCatalog(bidderDeps);

        // when and then
        assertThat(target.usersyncReadyBidders()).containsExactly("bidder-with-usersync");
    }

    @Test
    public void usersyncerByNameShouldReturnNullForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.usersyncerByName("unknown_bidder")).isEmpty();
    }

    @Test
    public void bidderByNameShouldReturnBidderForKnownBidder() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .bidder(bidder)
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.bidderByName(BIDDER)).isSameAs(bidder);
    }

    @Test
    public void nameByVendorIdShouldReturnBidderNameForVendorId() {
        // given
        final BidderInfo bidderInfo = BidderInfo.create(
                true,
                null,
                true,
                null,
                null,
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                null,
                99,
                true,
                false,
                CompressionType.NONE);

        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name(BIDDER)
                .deprecatedNames(emptyList())
                .bidder(bidder)
                .bidderInfo(bidderInfo)
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.nameByVendorId(99)).isEqualTo(BIDDER);
    }

    @Test
    public void bidderByNameShouldReturnNullForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.bidderByName("unknown_bidder")).isNull();
    }
}
