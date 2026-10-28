package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
public class BidderEnrichmentSamplerTest extends BaseOptableTest {

    @Mock
    private BidderCatalog bidderCatalog;

    private BidderEnrichmentSampler target;

    @BeforeEach
    public void setUp() {
        target = BidderEnrichmentSampler.of(bidderCatalog);
    }

    @Test
    public void sampleShouldReturnEmptySetWhenRequestHasNoImpressions() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void sampleShouldReturnEmptySetWhenImpHasNoExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(identity()))));

        // when
        final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void sampleShouldReturnEmptySetWhenImpExtHasNoBidderNodes() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(ext)))));

        // when
        final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void sampleShouldReturnEmptySetWhenBidderNodeIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt())))));

        // when
        final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void sampleShouldIncludeAllBiddersWhenDefaultPercentageIs100() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA", "bidderB"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

            // then
            assertThat(result).containsExactlyInAnyOrder("bidderA", "bidderB");
        }
    }

    @Test
    public void sampleShouldExcludeAllBiddersWhenDefaultPercentageIsNegative() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA", "bidderB"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenSampleProperties(-1, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void sampleShouldExcludeBidderWhenRandomValueEqualsPercentage() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(50)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(50, Collections.emptyMap()));

            // then
            assertThat(result).isEmpty();
        }
    }

    @Test
    public void sampleShouldIncludeBidderWhenRandomValueIsBelowPercentage() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(49)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(50, Collections.emptyMap()));

            // then
            assertThat(result).containsExactly("bidderA");
        }
    }

    @Test
    public void sampleShouldExcludeBidderWhenRandomValueExceedsPercentage() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(51)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(50, Collections.emptyMap()));

            // then
            assertThat(result).isEmpty();
        }
    }

    @Test
    public void sampleShouldExcludeBidderWhenPercentageIsZero() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenSampleProperties(0, Collections.emptyMap()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void sampleShouldUseBidderSpecificPercentageWhenAvailable() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA", "bidderB"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(-1, Map.of("bidderA", 100)));

            // then
            assertThat(result).containsExactly("bidderA");
        }
    }

    @Test
    public void sampleShouldUseBidderSpecificPercentageWhenBidderIsRequestAlias() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(request -> request
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(Map.of("aliasB", "bidderB"))
                        .build()))
                .imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA", "aliasB"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(-1, Map.of("bidderB", 100)));

            // then
            assertThat(result).containsExactly("aliasB");
        }
    }

    @Test
    public void sampleShouldIgnoreRequestAliasWhenBidderIsValidCatalogName() {
        // given
        given(bidderCatalog.isValidName("bidderA")).willReturn(true);

        final BidRequest bidRequest = givenBidRequest(request -> request
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(Map.of("bidderA", "bidderB"))
                        .build()))
                .imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));

        // when
        final Set<String> result = target.sample(bidRequest, givenSampleProperties(-1, Map.of("bidderB", 100)));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void sampleShouldPreferBidderOwnPercentageOverResolvedBidderPercentage() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(Map.of("aliasB", "bidderB"))
                        .build()))
                .imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("aliasB"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest,
                    givenSampleProperties(-1, Map.of("aliasB", 100, "bidderB", 0)));

            // then
            assertThat(result).containsExactly("aliasB");
        }
    }

    @Test
    public void sampleShouldDeduplicateBiddersAppearingInMultipleImps() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final ObjectNode ext = givenPrebidBidderExt("bidderA");
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(List.of(
                givenImp(imp -> imp.ext(ext)),
                givenImp(imp -> imp.ext(ext)))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(0)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

            // then
            assertThat(result).containsExactly("bidderA");
        }
    }

    @Test
    public void sampleShouldReturnOldStyleImpExtBiddersWhenPrebidBidderNodeIsAbsent() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenOldStyleExt("bidderA", "bidderB"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

            // then
            assertThat(result).containsExactlyInAnyOrder("bidderA", "bidderB");
        }
    }

    @Test
    public void sampleShouldIgnoreOldStyleImpExtBiddersWhenImpHasPrebidBidderNode() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final ObjectNode ext = givenPrebidBidderExt("bidderA");
        ext.putObject("bidderB").put("param", "value");
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(ext)))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

            // then
            assertThat(result).containsExactly("bidderA");
        }
    }

    @Test
    public void sampleShouldFallBackToOldStyleImpExtBiddersWhenPrebidBidderNodeIsEmpty() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final ObjectNode ext = givenPrebidBidderExt();
        ext.putObject("bidderB").put("param", "value");
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(ext)))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

            // then
            assertThat(result).containsExactly("bidderB");
        }
    }

    @Test
    public void sampleShouldCombinePrebidAndOldStyleBiddersOfDifferentImps() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(request -> request.imp(List.of(
                givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))),
                givenImp(imp -> imp.ext(givenOldStyleExt("bidderB"))))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

            // then
            assertThat(result).containsExactlyInAnyOrder("bidderA", "bidderB");
        }
    }

    @Test
    public void sampleShouldNotTreatReservedOrNonObjectImpExtFieldsAsBidders() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final ObjectNode ext = givenOldStyleExt("bidderA", "context", "data", "skadn", "all", "general");
        ext.put("gpid", "gpid");
        ext.put("tid", "tid");
        ext.putObject("prebid").putObject("storedrequest").put("id", "id");
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(ext)))));

        try (MockedStatic<ThreadLocalRandom> ignored = givenRandomValue(99)) {
            // when
            final Set<String> result = target.sample(bidRequest, givenSampleProperties(100, Collections.emptyMap()));

            // then
            assertThat(result).containsExactly("bidderA");
        }
    }

    private static MockedStatic<ThreadLocalRandom> givenRandomValue(int value) {
        final MockedStatic<ThreadLocalRandom> threadLocalRandom = mockStatic(ThreadLocalRandom.class);
        final ThreadLocalRandom current = mock(ThreadLocalRandom.class);
        threadLocalRandom.when(ThreadLocalRandom::current).thenReturn(current);
        given(current.nextInt(100)).willReturn(value);
        return threadLocalRandom;
    }

    private OptableTargetingProperties givenSampleProperties(int defaultPct, Map<String, Integer> bidderPcts) {
        final OptableTargetingProperties props = new OptableTargetingProperties();
        props.setEnrichmentPercentage(defaultPct);
        props.setBidderEnrichmentPercentages(bidderPcts);
        return props;
    }

    private ObjectNode givenOldStyleExt(String... bidders) {
        final ObjectNode ext = mapper.createObjectNode();
        for (String bidder : bidders) {
            ext.putObject(bidder).put("param", "value");
        }
        return ext;
    }
}
