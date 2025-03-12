package org.prebid.server.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodes;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodesBidder;

import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class BidderAliasesTest {

    @Mock(strictness = LENIENT)
    private BidderCatalog bidderCatalog;

    @BeforeEach
    public void before() {
        given(bidderCatalog.isValidName(any())).willReturn(false);
        given(bidderCatalog.isActive(any())).willReturn(false);
    }

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
    public void resolveBidderShouldReturnInputWhenNoAliasesInRequestButAliasIsValidInBidderCatalog() {
        // given
        given(bidderCatalog.isValidName("alias")).willReturn(true);
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
    public void resolveBidderShouldReturnInputWhenAliasIsNotDefinedInRequestButAliasIsValidInBidderCatalog() {
        // given
        given(bidderCatalog.isValidName("alias")).willReturn(true);
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
    public void resolveBidderShouldDetectInBidderCatalogWhenItIsValid() {
        // given
        given(bidderCatalog.isValidName("alias")).willReturn(true);
        final BidderAliases aliases = BidderAliases.of(singletonMap("alias", "bidder"), null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveBidder("alias")).isEqualTo("alias");
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
    public void resolveAliasVendorIdShouldReturnVendorIdFromBidderCatalogWhenNoVendorIdsInRequest() {
        // given
        given(bidderCatalog.isActive("alias")).willReturn(true);
        given(bidderCatalog.vendorIdByName("alias")).willReturn(3);
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(3);
    }

    @Test
    public void resolveAliasVendorIdShouldReturnNullWhenVendorIdIsNotDefinedInRequest() {
        // given
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("anotherAlias", 2), bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isNull();
    }

    @Test
    public void resolveAliasVendorIdShouldReturnVendorIdFromBidderCatalogWhenVendorIdIsNotDefinedInRequest() {
        // given
        given(bidderCatalog.isActive("alias")).willReturn(true);
        given(bidderCatalog.vendorIdByName("alias")).willReturn(3);
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("anotherAlias", 2), bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(3);
    }

    @Test
    public void resolveAliasVendorIdShouldReturnVendorIdFromBidderCatalogWhenVendorIdIsInRequest() {
        // given
        given(bidderCatalog.isActive("alias")).willReturn(true);
        given(bidderCatalog.vendorIdByName("alias")).willReturn(3);
        final BidderAliases aliases = BidderAliases.of(null, singletonMap("alias", 2), bidderCatalog);

        // when and then
        assertThat(aliases.resolveAliasVendorId("alias")).isEqualTo(3);
    }

    @Test
    public void isAllowedAlternateBidderCodeShouldReturnFalseWhenAlternateBidderCodesAreDisabledForBidder() {
        // given
        final ExtRequestPrebidAlternateBidderCodes alternateBidderCodes = ExtRequestPrebidAlternateBidderCodes.builder()
                .enabled(true)
                .bidders(Map.of("bidder", ExtRequestPrebidAlternateBidderCodesBidder.builder()
                        .enabled(false)
                        .allowedBidderCodes(Set.of("seat"))
                        .build()))
                .build();
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog, alternateBidderCodes);

        // when and then
        assertThat(aliases.isAllowedAlternateBidderCode("bidder", "seat")).isFalse();
    }

    @Test
    public void isAllowedAlternateBidderCodeShouldReturnFalseWhenAlternateBidderCodesAreLackingSeat() {
        // given
        final ExtRequestPrebidAlternateBidderCodes alternateBidderCodes = ExtRequestPrebidAlternateBidderCodes.builder()
                .enabled(true)
                .bidders(Map.of("bidder", ExtRequestPrebidAlternateBidderCodesBidder.builder()
                        .enabled(true)
                        .allowedBidderCodes(Set.of("anotherSeat"))
                        .build()))
                .build();
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog, alternateBidderCodes);

        // when and then
        assertThat(aliases.isAllowedAlternateBidderCode("bidder", "seat")).isFalse();
    }

    @Test
    public void isAllowedAlternateBidderCodeShouldReturnFalseWhenAlternateBidderCodesAreLackingBidders() {
        // given
        final ExtRequestPrebidAlternateBidderCodes alternateBidderCodes = ExtRequestPrebidAlternateBidderCodes.builder()
                .enabled(true)
                .bidders(null)
                .build();
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog, alternateBidderCodes);

        // when and then
        assertThat(aliases.isAllowedAlternateBidderCode("bidder", "seat")).isFalse();
    }

    @Test
    public void isAllowedAlternateBidderCodeShouldReturnFalseWhenAlternateBidderCodesAreDisabled() {
        // given
        final ExtRequestPrebidAlternateBidderCodes alternateBidderCodes = ExtRequestPrebidAlternateBidderCodes.builder()
                .enabled(false)
                .bidders(Map.of("bidder", ExtRequestPrebidAlternateBidderCodesBidder.builder()
                        .enabled(true)
                        .allowedBidderCodes(Set.of("seat"))
                        .build()))
                .build();
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog, alternateBidderCodes);

        // when and then
        assertThat(aliases.isAllowedAlternateBidderCode("bidder", "seat")).isFalse();
    }

    @Test
    public void isAllowedAlternateBidderCodeShouldReturnTrueWhenAllowedAlternateCodesAreNull() {
        // given
        final ExtRequestPrebidAlternateBidderCodes alternateBidderCodes = ExtRequestPrebidAlternateBidderCodes.builder()
                .enabled(true)
                .bidders(Map.of("bidder", ExtRequestPrebidAlternateBidderCodesBidder.builder()
                        .enabled(true)
                        .allowedBidderCodes(null)
                        .build()))
                .build();
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog, alternateBidderCodes);

        // when and then
        assertThat(aliases.isAllowedAlternateBidderCode("bidder", "seat")).isTrue();
    }

    @Test
    public void isAllowedAlternateBidderCodeShouldReturnTrueWhenAllowedAlternateCodesContainsWildcard() {
        // given
        final ExtRequestPrebidAlternateBidderCodes alternateBidderCodes = ExtRequestPrebidAlternateBidderCodes.builder()
                .enabled(true)
                .bidders(Map.of("bidder", ExtRequestPrebidAlternateBidderCodesBidder.builder()
                        .enabled(true)
                        .allowedBidderCodes(Set.of("*"))
                        .build()))
                .build();
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog, alternateBidderCodes);

        // when and then
        assertThat(aliases.isAllowedAlternateBidderCode("bidder", "seat")).isTrue();
    }

    @Test
    public void isAllowedAlternateBidderCodeShouldReturnTrueWhenAllowedAlternateCodesContainsSeatCaseInsensitive() {
        // given
        final ExtRequestPrebidAlternateBidderCodes alternateBidderCodes = ExtRequestPrebidAlternateBidderCodes.builder()
                .enabled(true)
                .bidders(Map.of("BiDDEr", ExtRequestPrebidAlternateBidderCodesBidder.builder()
                        .enabled(true)
                        .allowedBidderCodes(Set.of("SeaT"))
                        .build()))
                .build();
        final BidderAliases aliases = BidderAliases.of(null, null, bidderCatalog, alternateBidderCodes);

        // when and then
        assertThat(aliases.isAllowedAlternateBidderCode("bidder", "seat")).isTrue();
    }
}
