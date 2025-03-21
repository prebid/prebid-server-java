package org.prebid.server.auction.adjustment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BidAdjustmentFactorResolverTest {

    private BidAdjustmentFactorResolver bidAdjustmentFactorResolver;

    @BeforeEach
    public void setUp() {
        bidAdjustmentFactorResolver = new BidAdjustmentFactorResolver();
    }

    @Test
    public void resolveShouldReturnOneIfAdjustmentsByMediaTypeAndBidderAreAbsent() {
        // when
        final BigDecimal result = bidAdjustmentFactorResolver.resolve(
                ImpMediaType.video,
                ExtRequestBidAdjustmentFactors.builder().build(),
                "bidder");

        // then
        assertThat(result).isEqualTo(BigDecimal.ONE);
    }

    @Test
    public void resolveShouldReturnBidderAdjustmentFactorIfAdjustmentsByTypeAreAbsentIgnoringCase() {
        // given
        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder().build();
        adjustmentFactors.addFactor("BIDder", BigDecimal.valueOf(3.456));

        // when
        final BigDecimal result = bidAdjustmentFactorResolver.resolve(ImpMediaType.video, adjustmentFactors, "bidDER");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(3.456));
    }

    @Test
    public void resolveShouldReturnAdjustmentByMediaTypeIfPresentIgnoringCase() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of("BIDder", BigDecimal.valueOf(1.234)),
                ImpMediaType.video_outstream, Map.of("bidder", BigDecimal.valueOf(2.345)),
                ImpMediaType.banner, Map.of("bidder", BigDecimal.valueOf(3.456))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder()
                        .mediatypes(adjustmentFactorsByMediaType)
                        .build();

        // when
        final BigDecimal result = bidAdjustmentFactorResolver.resolve(ImpMediaType.video, adjustmentFactors, "bidDER");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(1.234));
    }
}
