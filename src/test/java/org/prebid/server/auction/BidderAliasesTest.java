package org.prebid.server.auction;

import org.junit.Test;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public class BidderAliasesTest {

    @Test
    public void isAliasDefinedShouldReturnFalseWhenNoAliasesInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, null);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isFalse();
    }

    @Test
    public void isAliasDefinedShouldReturnFalseWhenAliasIsNotDefinedInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("anotherAlias", "bidder"), null);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isFalse();
    }

    @Test
    public void isAliasDefinedShouldDetectAliasInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("alias", "bidder"), null);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isTrue();
    }

    @Test
    public void resolveBidderShouldReturnInputWhenNoAliasesInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, null);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("alias");
    }

    @Test
    public void resolveBidderShouldReturnInputWhenAliasIsNotDefinedInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("anotherAlias", "bidder"), null);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("alias");
    }

    @Test
    public void resolveBidderShouldDetectAliasInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("alias", "bidder"), null);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("bidder");
    }

    @Test
    public void resolveAliasVendorIdShouldReturnNullWhenNoVendorIdsInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, null);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isNull();
    }

    @Test
    public void resolveAliasVendorIdShouldReturnNullWhenVendorIdIsNotDefinedInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("anotherAlias", 2));

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isNull();
    }

    @Test
    public void resolveAliasVendorIdShouldDetectVendorIdInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("alias", 2));

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(2);
    }
}
