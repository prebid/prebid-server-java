package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class BidderEnrichmentSamplerTest extends BaseOptableTest {

    @Mock
    private AliasesResolver aliasesResolver;

    @Mock
    private BidderAliases bidderAliases;

    @Mock
    private IntSupplier randomSupplier;

    private BidderEnrichmentSampler target;

    @BeforeEach
    public void setUp() {
        target = BidderEnrichmentSampler.of(aliasesResolver, randomSupplier);
        given(aliasesResolver.resolve(any())).willReturn(bidderAliases);
    }

    @Test
    public void diceShouldReturnEmptySetWhenRequestHasNoImpressions() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void diceShouldReturnEmptySetWhenImpHasNoExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(identity()))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void diceShouldReturnEmptySetWhenImpExtHasNoPrebidBidderNode() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(ext)))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void diceShouldReturnEmptySetWhenBidderNodeIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt())))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void diceShouldIncludeAllBiddersWhenDefaultPercentageIs100() {
        // given
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(99);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA", "bidderB"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).containsExactlyInAnyOrder("bidderA", "bidderB");
    }

    @Test
    public void diceShouldExcludeAllBiddersWhenDefaultPercentageIsNegative() {
        // given
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(0);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA", "bidderB"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(-1, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void diceShouldIncludeBidderWhenRandomValueEqualsPercentage() {
        // given
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(50);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(50, Collections.emptyMap()));

        // then
        assertThat(result).containsExactly("bidderA");
    }

    @Test
    public void diceShouldIncludeBidderWhenRandomValueIsBelowPercentage() {
        // given
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(49);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(50, Collections.emptyMap()));

        // then
        assertThat(result).containsExactly("bidderA");
    }

    @Test
    public void diceShouldExcludeBidderWhenRandomValueExceedsPercentage() {
        // given
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(51);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(50, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void diceShouldIncludeBidderWhenPercentageIsZeroAndRandomIsZero() {
        // given — 0% still includes exactly when random == 0
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(0);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(0, Collections.emptyMap()));

        // then
        assertThat(result).containsExactly("bidderA");
    }

    @Test
    public void diceShouldExcludeBidderWhenPercentageIsZeroAndRandomIsOne() {
        // given
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(1);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(0, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void diceShouldUseBidderSpecificPercentageWhenAvailable() {
        // given
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(99);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA", "bidderB"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(-1, Map.of("bidderA", 100)));

        // then
        assertThat(result).containsExactly("bidderA");
    }

    @Test
    public void diceShouldUseAliasSpecificPercentageWhenBidderResolvesToAlias() {
        // given
        given(bidderAliases.resolveBidder("bidderA")).willReturn("bidderA");
        given(bidderAliases.resolveBidder("bidderB")).willReturn("aliasB");
        given(randomSupplier.getAsInt()).willReturn(99);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenBidderExt("bidderA", "bidderB"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(-1, Map.of("aliasB", 100)));

        // then
        assertThat(result).containsExactly("bidderB");
    }

    @Test
    public void diceShouldDeduplicateBiddersAppearingInMultipleImps() {
        // given
        given(bidderAliases.resolveBidder(any())).willAnswer(inv -> inv.getArgument(0));
        given(randomSupplier.getAsInt()).willReturn(0);

        final ObjectNode ext = givenBidderExt("bidderA");
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(List.of(
                givenImp(imp -> imp.ext(ext)),
                givenImp(imp -> imp.ext(ext)))));

        // when
        final Set<String> result = target.sample(bidRequest, givenDiceProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).containsExactly("bidderA");
    }

    private OptableTargetingProperties givenDiceProperties(int defaultPct, Map<String, Integer> bidderPcts) {
        final OptableTargetingProperties props = new OptableTargetingProperties();
        props.setEnrichmentPercentage(defaultPct);
        props.setBidderEnrichmentPercentages(bidderPcts);
        return props;
    }

    private ObjectNode givenBidderExt(String... bidders) {
        final ObjectNode bidderNode = mapper.createObjectNode();
        for (String bidder : bidders) {
            bidderNode.put(bidder, "value");
        }
        final ObjectNode prebidNode = mapper.createObjectNode();
        prebidNode.set("bidder", bidderNode);
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", prebidNode);
        return ext;
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }
}
