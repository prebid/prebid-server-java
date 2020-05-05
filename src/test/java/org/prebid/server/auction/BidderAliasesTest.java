package org.prebid.server.auction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.BidderCatalog;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyZeroInteractions;

public class BidderAliasesTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    @Before
    public void setUp() {
        given(bidderCatalog.isActive(anyString())).willReturn(true);
    }

    @Test
    public void isAliasDefinedShouldQueryCatalogWhenNoAliasesInRequest() {
        // given
        given(bidderCatalog.isAlias(anyString())).willReturn(true);

        final BidderAliases aliases = BidderAliases.of(bidderCatalog);

        // when and then
        assertThat(aliases.isAliasDefined("alias")).isTrue();
    }

    @Test
    public void isAliasDefinedShouldQueryCatalogWhenAliasIsNotDefinedInRequest() {
        // given
        given(bidderCatalog.isAlias(anyString())).willReturn(true);

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
        given(bidderCatalog.nameByAlias(anyString())).willReturn(null);

        final BidderAliases aliases = BidderAliases.of(bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("alias");
    }

    @Test
    public void resolveBidderShouldReturnInputWhenNoAliasesInRequestAndInactiveInCatalog() {
        // given
        given(bidderCatalog.nameByAlias(anyString())).willReturn("bidder");
        given(bidderCatalog.isActive(any())).willReturn(false);

        final BidderAliases aliases = BidderAliases.of(bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("alias");
    }

    @Test
    public void resolveBidderShouldQueryCatalogWhenNoAliasesInRequest() {
        // given
        given(bidderCatalog.nameByAlias(anyString())).willReturn("bidder");

        final BidderAliases aliases = BidderAliases.of(bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("bidder");
    }

    @Test
    public void resolveBidderShouldQueryCatalogWhenAliasIsNotDefinedInRequest() {
        // given
        given(bidderCatalog.nameByAlias(eq("alias"))).willReturn("bidder");

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
    public void resolveAliasVendorIdShouldReturnNullWhenNoVendorIdsInRequestAndInCatalog() {
        // given
        given(bidderCatalog.vendorIdByName(anyString())).willReturn(null);

        final BidderAliases aliases = BidderAliases.of(bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isNull();
    }

    @Test
    public void resolveAliasVendorIdShouldReturnNullWhenNoVendorIdsInRequestAndInactiveInCatalog() {
        // given
        given(bidderCatalog.vendorIdByName(anyString())).willReturn(1);
        given(bidderCatalog.isActive(anyString())).willReturn(false);

        final BidderAliases aliases = BidderAliases.of(bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isNull();
    }

    @Test
    public void resolveAliasVendorIdShouldQueryCatalogWhenNoVendorIdsInRequest() {
        // given
        given(bidderCatalog.vendorIdByName(anyString())).willReturn(1);
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final BidderAliases aliases = BidderAliases.of(bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(1);
    }

    @Test
    public void resolveAliasVendorIdShouldQueryCatalogWhenVendorIdIsNotDefinedInRequest() {
        // given
        given(bidderCatalog.vendorIdByName(eq("alias"))).willReturn(1);

        final BidderAliases aliases = BidderAliases.of(null, singletonMap("anotherAlias", 2), bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(1);
    }

    @Test
    public void resolveAliasVendorIdShouldDetectVendorIdInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("alias", 2), bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(2);

        verifyZeroInteractions(bidderCatalog);
    }

    @Test
    public void resolveAliasVendorIdShouldResolveAliasWhenQueryingCatalog() {
        // given
        given(bidderCatalog.nameByAlias(eq("alias"))).willReturn("bidder");
        given(bidderCatalog.vendorIdByName(eq("bidder"))).willReturn(1);

        final BidderAliases aliases = BidderAliases.of(bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(1);
    }
}
