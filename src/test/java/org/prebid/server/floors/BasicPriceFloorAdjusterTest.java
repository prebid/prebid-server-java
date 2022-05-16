package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.AdjustmentFactorResolver;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class BasicPriceFloorAdjusterTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AdjustmentFactorResolver adjustmentFactorResolver;

    private BasicPriceFloorAdjuster basicPriceFloorAdjuster;

    @Before
    public void setUp() {
        given(adjustmentFactorResolver.resolve(anySet(), any(), any())).willReturn(BigDecimal.ONE);

        basicPriceFloorAdjuster = new BasicPriceFloorAdjuster(adjustmentFactorResolver);
    }

    @Test
    public void adjustForImpShouldCallAdjustmentFactorResolverAndApplyFactor() {
        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, givenBidRequest(identity()), null);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
        verify(adjustmentFactorResolver).resolve(anySet(), any(), any());
    }

    @Test
    public void adjustForImpShouldNotApplyFactorIfAdjustmentDisabledByAccount() {
        // when
        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .adjustForBidAdjustment(false)
                                .build())
                        .build())
                .build();
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, givenBidRequest(identity()), account);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldNotApplyFactorIfAdjustmentDisabledByRequest() {
        // when
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidAdjustmentFactors.builder()
                                .mediatypes(givenMediaTypes(Map.of(
                                        ImpMediaType.video,
                                        Map.of(RUBICON, BigDecimal.valueOf(0.85D)))))
                                .build())
                        .floors(PriceFloorRules.builder()
                                .enforcement(PriceFloorEnforcement.builder()
                                        .bidAdjustment(false)
                                        .build())
                                .build())
                        .build())));

        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, bidRequest, null);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldApplyNoAdjustmentsIfBidAdjustmentsFactorIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder().bidadjustmentfactors(null).build())));
        final Imp imp = givenImp(identity());

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster.adjustForImp(imp, RUBICON, bidRequest, null);

        // then
        assertThat(adjustedBidFloor).isEqualTo(imp.getBidfloor());
    }

    @Test
    public void adjustForImpShouldReturnNullIfImpBidFloorIsNotPresent() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder.bidfloor(null));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(imp, RUBICON, givenBidRequest(identity()), null);

        // then
        assertThat(adjustedBidFloor).isNull();
    }

    @Test
    public void adjustForImpShouldReturnBidFloorNotFactoredByOtherBidder() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidAdjustmentFactors.builder()
                                .mediatypes(givenMediaTypes(Map.of(
                                        ImpMediaType.video,
                                        Map.of("bidder", BigDecimal.valueOf(0.8D)))))
                                .build())
                        .build())));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, bidRequest, null);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldReturnFactoredOfOneIfExtBidAdjustmentsFactorMediaTypesIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidAdjustmentFactors.builder()
                                .mediatypes(null)
                                .build())
                        .build())));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, bidRequest, null);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldReturnFactorOfOneIfNoMediaTypeInImpression() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final Imp imp = givenImp(impBuilder -> impBuilder.video(null));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(imp, RUBICON, bidRequest, null);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldSkipMediaTypeIfNoMediaTypesOfImpFound() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidAdjustmentFactors.builder()
                                .mediatypes(givenMediaTypes(Map.of(
                                        ImpMediaType.video_outstream,
                                        Map.of(RUBICON, BigDecimal.valueOf(0.8D)))))
                                .build())
                        .build())));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, bidRequest, null);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(
                        BidRequest.builder()
                                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                        .bidadjustmentfactors(ExtRequestBidAdjustmentFactors.builder()
                                                .mediatypes(givenMediaTypes(Map.of(
                                                        ImpMediaType.video,
                                                        Map.of(RUBICON, BigDecimal.valueOf(0.85D)))))
                                                .build())
                                        .build())))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .bidfloor(BigDecimal.TEN)
                        .video(Video.builder().placement(1).build())
                        .bidfloorcur("USD")
                        .ext(jacksonMapper.mapper().createObjectNode()))
                .build();
    }

    private static EnumMap<ImpMediaType, Map<String, BigDecimal>> givenMediaTypes(
            Map<ImpMediaType, Map<String, BigDecimal>> values) {
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> mediaTypes = new EnumMap<>(ImpMediaType.class);
        mediaTypes.putAll(values);

        return mediaTypes;
    }
}
