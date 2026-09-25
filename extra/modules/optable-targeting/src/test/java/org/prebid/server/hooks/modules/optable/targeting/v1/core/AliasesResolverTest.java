package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class AliasesResolverTest {

    @Mock
    private BidderCatalog bidderCatalog;

    private AliasesResolver target;

    @BeforeEach
    public void setUp() {
        target = AliasesResolver.of(bidderCatalog);
    }

    @Test
    public void resolveShouldReturnEmptyBidderAliasesWhenBidRequestIsNull() {
        // when
        final BidderAliases result = target.resolve(null);

        // then
        assertThat(result.isAliasDefined("anyAlias")).isFalse();
    }

    @Test
    public void resolveShouldReturnEmptyBidderAliasesWhenBidRequestHasNoExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidderAliases result = target.resolve(bidRequest);

        // then
        assertThat(result.isAliasDefined("anyAlias")).isFalse();
    }

    @Test
    public void resolveShouldReturnEmptyBidderAliasesWhenBidRequestHasNoExtPrebid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.empty())
                .build();

        // when
        final BidderAliases result = target.resolve(bidRequest);

        // then
        assertThat(result.isAliasDefined("anyAlias")).isFalse();
    }

    @Test
    public void resolveShouldReturnBidderAliasesWithValuesWhenBidRequestHasAliases() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(Map.of("alias", "bidder"))
                        .aliasgvlids(Map.of("alias", 123))
                        .build()))
                .build();

        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        // when
        final BidderAliases result = target.resolve(bidRequest);

        // then
        assertThat(result.isAliasDefined("alias")).isTrue();
        assertThat(result.resolveBidder("alias")).isEqualTo("bidder");
        assertThat(result.resolveAliasVendorId("alias")).isEqualTo(123);
    }
}
