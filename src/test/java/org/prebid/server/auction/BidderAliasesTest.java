package org.prebid.server.auction;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.BidderCatalog;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyZeroInteractions;

public class BidderAliasesTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    @Test
    public void isAliasDefinedShouldQueryCatalogWhenNoAliasesInRequest() {
        // given
        given(bidderCatalog.isAlias(any())).willReturn(true);

        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isTrue();
    }

    @Test
    public void isAliasDefinedShouldQueryCatalogWhenAliasIsNotDefinedInRequest() {
        // given
        given(bidderCatalog.isAlias(any())).willReturn(true);

        final BidderAliases aliases = BidderAliases.of(singletonMap("anotherAlias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isTrue();
    }

    @Test
    public void isAliasDefinedShouldDetectAliasInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("alias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isTrue();

        verifyZeroInteractions(bidderCatalog);
    }

    @Test
    public void resolveBidderShouldReturnInputWhenNoAliasesInRequestAndInCatalog() {
        // given
        given(bidderCatalog.nameByAlias(any())).willReturn(null);

        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("alias");
    }

    @Test
    public void resolveBidderShouldQueryCatalogWhenNoAliasesInRequest() {
        // given
        given(bidderCatalog.nameByAlias(any())).willReturn("bidder");

        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("bidder");
    }

    @Test
    public void resolveBidderShouldQueryCatalogWhenAliasIsNotDefinedInRequest() {
        // given
        given(bidderCatalog.nameByAlias(any())).willReturn("bidder");

        final BidderAliases aliases = BidderAliases.of(singletonMap("anotherAlias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("bidder");
    }

    @Test
    public void resolveBidderShouldDetectAliasInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(singletonMap("alias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("bidder");

        verifyZeroInteractions(bidderCatalog);
    }

    @Test
    public void resolveAliasVendorIdShouldReturnNullWhenNoVendorIdsInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isNull();
    }

    @Test
    public void resolveAliasVendorIdShouldReturnIdFromRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("alias", 1), bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(1);
    }
}
