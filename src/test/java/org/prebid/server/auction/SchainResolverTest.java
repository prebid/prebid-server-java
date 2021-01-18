package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchainNode;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class SchainResolverTest extends VertxTest {

    private SchainResolver schainResolver;

    @Before
    public void setUp() {
        schainResolver = SchainResolver.create(null, jacksonMapper);
    }

    @Test
    public void shouldResolveSchains() {
        // given
        final ObjectNode schainExtObjectNode = mapper.createObjectNode().put("any", "any");

        final ExtRequestPrebidSchainSchainNode specificNodes = ExtRequestPrebidSchainSchainNode.of(
                "asi", "sid", 1, "rid", "name", "domain", schainExtObjectNode);
        final ExtRequestPrebidSchainSchain specificSchain = ExtRequestPrebidSchainSchain.of(
                "ver", 1, singletonList(specificNodes), schainExtObjectNode);
        final ExtRequestPrebidSchain schainForBidders = ExtRequestPrebidSchain.of(
                asList("bidder1", "bidder2"), specificSchain);

        final ExtRequestPrebidSchainSchainNode generalNodes = ExtRequestPrebidSchainSchainNode.of(
                "t", null, 0, "a", null, "ads", null);
        final ExtRequestPrebidSchainSchain generalSchain = ExtRequestPrebidSchainSchain.of(
                "t", 123, singletonList(generalNodes), null);
        final ExtRequestPrebidSchain allSchain = ExtRequestPrebidSchain.of(singletonList("*"), generalSchain);

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .schains(asList(schainForBidders, allSchain))
                        .build()))
                .build();

        // when
        final Map<String, ExtRequestPrebidSchainSchain> result = schainResolver.resolve(bidRequest);

        // then
        assertThat(result).containsOnly(
                entry("bidder1", specificSchain),
                entry("bidder2", specificSchain),
                entry("*", generalSchain));
    }

    @Test
    public void shouldIgnoreDuplicatedBidderSchains() {
        // given
        final ExtRequestPrebidSchain schain1 = ExtRequestPrebidSchain.of(
                singletonList("bidder"), ExtRequestPrebidSchainSchain.of("ver1", null, null, null));
        final ExtRequestPrebidSchain schain2 = ExtRequestPrebidSchain.of(
                singletonList("bidder"), ExtRequestPrebidSchainSchain.of("ver2", null, null, null));

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .schains(asList(schain1, schain2))
                        .build()))
                .build();

        // when and then
        assertThat(schainResolver.resolve(bidRequest)).isEmpty();
    }
}