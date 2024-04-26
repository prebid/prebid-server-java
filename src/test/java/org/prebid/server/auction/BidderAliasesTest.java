package org.prebid.server.auction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.bidder.BidderCatalog;

import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class BidderAliasesTest {

    @Mock
    private BidderCatalog bidderCatalog;

    @Test
    public void isAliasDefinedShouldReturnFalseWhenNoAliasesInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isFalse();
    }

    @Test
    public void isAliasDefinedShouldReturnFalseWhenAliasIsNotDefinedInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("anotherAlias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isFalse();
    }

    @Test
    public void isAliasDefinedShouldDetectAliasInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("alias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isTrue();
    }

    @Test
    public void resolveBidderShouldReturnInputWhenNoAliasesInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("alias");
    }

    @Test
    public void resolveBidderShouldReturnInputWhenAliasIsNotDefinedInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("anotherAlias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("alias");
    }

    @Test
    public void resolveBidderShouldDetectAliasInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("alias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("bidder");
    }

    @Test
    public void isSameShouldReturnTrueIfBiddersSameConsideringAliases() {
        // given
        final BidderAliases aliases = BidderAliases.of(
                Map.of("alias1", "bidder", "alias2", "bidder"),
                null,
                bidderCatalog);

        // when and then
        assertThat(aliases.isSame("bidder", "bidder")).isTrue();
        assertThat(aliases.isSame("alias1", "bidder")).isTrue();
        assertThat(aliases.isSame("alias2", "bidder")).isTrue();
        assertThat(aliases.isSame("alias1", "alias2")).isTrue();
    }

    @Test
    public void isSameShouldReturnTrueIfBiddersSameConsideringAliasesIgnoringCase() {
        // given
        final BidderAliases aliases = BidderAliases.of(
                Map.of("alias1", "bidder", "alias2", "BiDdEr"),
                null,
                bidderCatalog);

        // when and then
        assertThat(aliases.isSame("BIDder", "bidDER")).isTrue();
        assertThat(aliases.isSame("alias1", "bidDER")).isTrue();
        assertThat(aliases.isSame("alias2", "bidDER")).isTrue();
        assertThat(aliases.isSame("alias1", "alias2")).isTrue();
    }

    @Test
    public void resolveAliasVendorIdShouldReturnNullWhenNoVendorIdsInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isNull();
    }

    @Test
    public void resolveAliasVendorIdShouldReturnNullWhenVendorIdIsNotDefinedInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("anotherAlias", 2), bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isNull();
    }

    @Test
    public void resolveAliasVendorIdShouldDetectVendorIdInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("alias", 2), bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(2);
    }
}
