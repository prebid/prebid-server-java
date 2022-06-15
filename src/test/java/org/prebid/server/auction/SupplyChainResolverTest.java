package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.SupplyChainNode;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class SupplyChainResolverTest extends VertxTest {

    private SupplyChainResolver supplyChainResolver;

    @Before
    public void setUp() {
        supplyChainResolver = SupplyChainResolver.create(null, jacksonMapper);
    }

    @Test
    public void shouldResolveSchainsWhenCatchAllPresent() {
        // given
        final SupplyChainNode specificNodes = SupplyChainNode.of("asi", "sid", "rid", "name", "domain", 1, null);
        final SupplyChain specificSchain = SupplyChain.of(1, singletonList(specificNodes), "ver", null);
        final ExtRequestPrebidSchain schainForBidders = ExtRequestPrebidSchain.of(
                asList("bidder1", "bidder2"), specificSchain);

        final SupplyChainNode generalNodes = SupplyChainNode.of("t", null, "a", null, "ads", 0, null);
        final SupplyChain generalSchain = SupplyChain.of(123, singletonList(generalNodes), "t", null);
        final ExtRequestPrebidSchain allSchain = ExtRequestPrebidSchain.of(singletonList("*"), generalSchain);

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .schains(asList(schainForBidders, allSchain))
                        .build()))
                .build();

        // when and then
        assertThat(supplyChainResolver.resolveForBidder("bidder1", bidRequest)).isSameAs(specificSchain);
        assertThat(supplyChainResolver.resolveForBidder("bidder2", bidRequest)).isSameAs(specificSchain);
        assertThat(supplyChainResolver.resolveForBidder("bidder3", bidRequest)).isSameAs(generalSchain);
    }

    @Test
    public void shouldReturnNullWhenAbsentForBidderAndNoCatchAll() {
        // given
        final SupplyChainNode specificNodes = SupplyChainNode.of("asi", "sid", "rid", "name", "domain", 1, null);
        final SupplyChain specificSchain = SupplyChain.of(1, singletonList(specificNodes), "ver", null);
        final ExtRequestPrebidSchain schainForBidders = ExtRequestPrebidSchain.of(
                singletonList("bidder1"), specificSchain);

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .schains(singletonList(schainForBidders))
                        .build()))
                .build();

        // when and then
        assertThat(supplyChainResolver.resolveForBidder("bidder2", bidRequest)).isNull();
    }

    @Test
    public void shouldIgnoreDuplicatedBidderSchains() {
        // given
        final ExtRequestPrebidSchain schain1 = ExtRequestPrebidSchain.of(
                singletonList("bidder"), SupplyChain.of(null, null, "ver1", null));
        final ExtRequestPrebidSchain schain2 = ExtRequestPrebidSchain.of(
                singletonList("bidder"), SupplyChain.of(null, null, "ver2", null));

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .schains(asList(schain1, schain2))
                        .build()))
                .build();

        // when and then
        assertThat(supplyChainResolver.resolveForBidder("bidder", bidRequest)).isNull();
    }

    @Test
    public void shouldInjectGlobalNodeIntoResolvedSchain() {
        // given
        supplyChainResolver = SupplyChainResolver.create(
                "{\"asi\": \"pbshostcompany.com\", \"sid\":\"00001\"}",
                jacksonMapper);

        final SupplyChainNode node = SupplyChainNode.of("asi", "sid", "rid", "name", "domain", 1, null);
        final ExtRequestPrebidSchain schainEntry = ExtRequestPrebidSchain.of(
                singletonList("bidder"), SupplyChain.of(1, singletonList(node), "ver", null));

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .schains(singletonList(schainEntry))
                        .build()))
                .build();

        // when and then
        final SupplyChainNode globalNode = SupplyChainNode.of(
                "pbshostcompany.com", "00001", null, null, null, null, null);
        final SupplyChain expectedSchain = SupplyChain.of(1, asList(node, globalNode), "ver", null);
        assertThat(supplyChainResolver.resolveForBidder("bidder", bidRequest)).isEqualTo(expectedSchain);
    }

    @Test
    public void shouldReturnSchainWithGlobalNodeOnly() {
        // given
        supplyChainResolver = SupplyChainResolver.create(
                "{\"asi\": \"pbshostcompany.com\", \"sid\":\"00001\"}",
                jacksonMapper);

        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        final SupplyChainNode globalNode = SupplyChainNode.of(
                "pbshostcompany.com", "00001", null, null, null, null, null);
        final SupplyChain expectedSchain = SupplyChain.of(null, singletonList(globalNode), null, null);
        assertThat(supplyChainResolver.resolveForBidder("bidder", bidRequest)).isEqualTo(expectedSchain);
    }

    @Test
    public void shouldInjectGlobalNodeIntoExistingSchain() {
        // given
        supplyChainResolver = SupplyChainResolver.create(
                "{\"asi\": \"pbshostcompany.com\", \"sid\":\"00001\"}",
                jacksonMapper);

        final SupplyChainNode node = SupplyChainNode.of("asi", "sid", "rid", "name", "domain", 1, null);
        final SupplyChain schain = SupplyChain.of(1, singletonList(node), "ver", null);

        final BidRequest bidRequest = BidRequest.builder()
                .source(Source.builder()
                        .schain(schain)
                        .build())
                .build();

        // when and then
        final SupplyChainNode globalNode = SupplyChainNode.of(
                "pbshostcompany.com", "00001", null, null, null, null, null);
        final SupplyChain expectedSchain = SupplyChain.of(1, asList(node, globalNode), "ver", null);
        assertThat(supplyChainResolver.resolveForBidder("bidder", bidRequest)).isEqualTo(expectedSchain);
    }
}
