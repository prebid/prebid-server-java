package org.prebid.server.bidder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.MediaType;
import org.prebid.server.spring.config.bidder.model.Ortb;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class BidderCatalogTest {

    @Mock
    private Bidder<?> bidder;

    private BidderCatalog target;

    @Test
    public void isValidNameShouldReturnTrueForKnownBidderIgnoringCase() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(emptyList())
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.isValidName("bidDER")).isTrue();
    }

    @Test
    public void isValidNameShouldReturnFalseForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.isValidName("unknown_bidder")).isFalse();
    }

    @Test
    public void isDeprecatedNameShouldReturnTrueForDeprecatedBidderIgnoringCase() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("bidder")
                .deprecatedNames(singletonList("depreCATed"))
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.isDeprecatedName("DEPrecated")).isTrue();
    }

    @Test
    public void isDeprecatedNameShouldReturnFalseForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.isDeprecatedName("unknown_bidder")).isFalse();
    }

    @Test
    public void errorForDeprecatedNameShouldReturnErrorForDeprecatedBidderIgnoringCase() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(singletonList("depreCATed"))
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.errorForDeprecatedName("DEPrecated"))
                .isEqualTo("depreCATed has been deprecated and is no longer available. Use BIDder instead.");
    }

    @Test
    public void metaInfoByNameShouldReturnMetaInfoForKnownBidderIgnoringCase() {
        // given
        final BidderInfo bidderInfo = BidderInfo.create(
                true,
                null,
                false,
                true,
                null,
                null,
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                singletonList(MediaType.AUDIO),
                null,
                99,
                null,
                true,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);

        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(emptyList())
                .bidderInfo(bidderInfo)
                .build()));

        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.bidderInfoByName("bidDER")).isEqualTo(bidderInfo);
    }

    @Test
    public void isAliasShouldReturnTrueForAliasIgnoringCase() {
        // given
        final BidderInfo bidderInfo = BidderInfo.create(
                true,
                null,
                false,
                true,
                null,
                null,
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                singletonList(MediaType.AUDIO),
                null,
                99,
                null,
                true,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);

        final BidderInstanceDeps bidderInstanceDeps = BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(emptyList())
                .bidderInfo(bidderInfo)
                .build();

        final BidderInfo aliasInfo = BidderInfo.create(
                true,
                null,
                false,
                true,
                null,
                "BIDder",
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                singletonList(MediaType.AUDIO),
                null,
                99,
                null,
                true,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);

        final BidderInstanceDeps aliasInstanceDeps = BidderInstanceDeps.builder()
                .name("ALIas")
                .deprecatedNames(emptyList())
                .bidderInfo(aliasInfo)
                .build();

        final BidderDeps bidderDeps = BidderDeps.of(List.of(bidderInstanceDeps, aliasInstanceDeps));

        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.isAlias("alIAS")).isTrue();
    }

    @Test
    public void resolveBaseBidderShouldReturnBaseBidderName() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("alias")
                .bidderInfo(BidderInfo.create(
                        true,
                        null,
                        false,
                        true,
                        null,
                        "bidder",
                        null,
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        null,
                        0,
                        null,
                        true,
                        false,
                        CompressionType.NONE,
                        Ortb.of(false),
                        0L))
                .deprecatedNames(emptyList())
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.resolveBaseBidder("alias")).isEqualTo("bidder");
    }

    @Test
    public void metaInfoByNameShouldReturnNullForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.bidderInfoByName("unknown_bidder")).isNull();
    }

    @Test
    public void usersyncerByNameShouldReturnUsersyncerForKnownBidderIgnoringCase() {
        // given
        final Usersyncer usersyncer = Usersyncer.of("name", null, null);
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(emptyList())
                .usersyncer(usersyncer)
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.usersyncerByName("bidDER")).contains(usersyncer);
    }

    @Test
    public void cookieFamilyNameShouldReturnCookieFamilyForKnownBidderIgnoringCase() {
        // given
        final Usersyncer usersyncer = Usersyncer.of("name", null, null);
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(emptyList())
                .usersyncer(usersyncer)
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.cookieFamilyName("bidDER")).contains("name");
    }

    @Test
    public void usersyncReadyBiddersShouldReturnBiddersThatCanSync() {
        // given
        final BidderInfo infoOfBidderWithUsersyncConfig = BidderInfo.create(
                true,
                null,
                false,
                true,
                null,
                "bidder-with-usersync",
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                singletonList(MediaType.AUDIO),
                null,
                99,
                null,
                true,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);

        final BidderInfo infoOfBidderWithoutUsersyncConfig = BidderInfo.create(
                true,
                null,
                false,
                true,
                null,
                "bidder-without-usersync",
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                singletonList(MediaType.AUDIO),
                null,
                99,
                null,
                true,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);

        final BidderInfo infoOfDisabledBidderWithUsersyncConfig = BidderInfo.create(
                false,
                null,
                false,
                true,
                null,
                "bidder-with-usersync",
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                singletonList(MediaType.AUDIO),
                null,
                99,
                null,
                true,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);

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
    public void bidderByNameShouldReturnBidderForKnownBidderIgnoringCase() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(emptyList())
                .bidder(bidder)
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.bidderByName("bidDER")).isSameAs(bidder);
    }

    @Test
    public void nameByVendorIdShouldReturnBidderNameForVendorId() {
        // given
        final BidderInfo bidderInfo = BidderInfo.create(
                true,
                null,
                false,
                true,
                null,
                null,
                "test@email.com",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.VIDEO),
                singletonList(MediaType.AUDIO),
                null,
                99,
                null,
                true,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);

        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(emptyList())
                .bidder(bidder)
                .bidderInfo(bidderInfo)
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.nameByVendorId(99)).isEqualTo("BIDder");
    }

    @Test
    public void bidderByNameShouldReturnNullForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.bidderByName("unknown_bidder")).isNull();
    }

    @Test
    public void configuredNameShouldReturnOriginalBidderNameForKnownBidderIgnoringCase() {
        // given
        final BidderDeps bidderDeps = BidderDeps.of(singletonList(BidderInstanceDeps.builder()
                .name("BIDder")
                .deprecatedNames(emptyList())
                .build()));
        target = new BidderCatalog(singletonList(bidderDeps));

        // when and then
        assertThat(target.configuredName("bidDER")).isEqualTo("BIDder");
    }

    @Test
    public void configuredNameShouldReturnNullForUnknownBidder() {
        // given
        target = new BidderCatalog(emptyList());

        // when and then
        assertThat(target.configuredName("unknown_bidder")).isNull();
    }
}
