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
import org.prebid.server.auction.adjustment.FloorAdjustmentFactorResolver;
import org.prebid.server.bidder.model.Price;
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
import java.util.ArrayList;
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
    private FloorAdjustmentFactorResolver floorAdjustmentFactorResolver;

    private BasicPriceFloorAdjuster target;

    @Before
    public void setUp() {
        given(floorAdjustmentFactorResolver.resolve(anySet(), any(), any())).willReturn(BigDecimal.ONE);

        target = new BasicPriceFloorAdjuster(floorAdjustmentFactorResolver);
    }

    @Test
    public void adjustForImpShouldCallAdjustmentFactorResolverAndApplyFactor() {
        // given
        given(floorAdjustmentFactorResolver.resolve(anySet(), any(), any())).willReturn(new BigDecimal("0.1"));

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                givenImp(identity()),
                RUBICON,
                givenBidRequest(identity()),
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", new BigDecimal(100)));
        verify(floorAdjustmentFactorResolver).resolve(anySet(), any(), any());
    }

    @Test
    public void adjustForImpShouldNotApplyFactorIfAdjustmentDisabledByAccount() {
        // given
        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .adjustForBidAdjustment(false)
                                .build())
                        .build())
                .build();

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                givenImp(identity()),
                RUBICON,
                givenBidRequest(identity()),
                account,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void adjustForImpShouldNotApplyFactorIfAdjustmentDisabledByRequest() {
        // given
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

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                givenImp(identity()),
                RUBICON,
                bidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void adjustForImpShouldApplyNoAdjustmentsIfBidAdjustmentsFactorIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder().bidadjustmentfactors(null).build())));
        final Imp imp = givenImp(identity());

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                imp,
                RUBICON,
                bidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", imp.getBidfloor()));
    }

    @Test
    public void adjustForImpShouldReturnNullIfImpBidFloorIsNotPresent() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder.bidfloor(null));

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                imp,
                RUBICON,
                givenBidRequest(identity()),
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", null));
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
        final Price adjustedBidPrice = target.adjustForImp(
                givenImp(identity()),
                RUBICON,
                bidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
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
        final Price adjustedBidPrice = target.adjustForImp(
                givenImp(identity()),
                RUBICON,
                bidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void adjustForImpShouldReturnFactorOfOneIfNoMediaTypeInImpression() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final Imp imp = givenImp(impBuilder -> impBuilder.video(null));

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                imp,
                RUBICON,
                bidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
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
        final Price adjustedBidPrice = target.adjustForImp(
                givenImp(identity()),
                RUBICON,
                bidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void revertAdjustmentForImpShouldCallAdjustmentFactorResolverAndApplyFactor() {
        // given
        given(floorAdjustmentFactorResolver.resolve(anySet(), any(), any())).willReturn(new BigDecimal("0.1"));

        // when
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                givenImp(identity()),
                RUBICON,
                givenBidRequest(identity()),
                null);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.ONE));
        verify(floorAdjustmentFactorResolver).resolve(anySet(), any(), any());
    }

    @Test
    public void revertAdjustmentForImpShouldNotApplyFactorIfAdjustmentDisabledByAccount() {
        // given
        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .adjustForBidAdjustment(false)
                                .build())
                        .build())
                .build();

        // when
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                givenImp(identity()),
                RUBICON,
                givenBidRequest(identity()),
                account);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void revertAdjustmentForImpShouldNotApplyFactorIfAdjustmentDisabledByRequest() {
        // given
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

        // when
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                givenImp(identity()),
                RUBICON,
                bidRequest,
                null);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void revertAdjustmentForImpShouldApplyNoAdjustmentsIfBidAdjustmentsFactorIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder().bidadjustmentfactors(null).build())));
        final Imp imp = givenImp(identity());

        // when
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                imp,
                RUBICON,
                bidRequest,
                null);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", imp.getBidfloor()));
    }

    @Test
    public void revertAdjustmentForImpShouldReturnNullIfImpBidFloorIsNotPresent() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder.bidfloor(null));

        // when
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                imp,
                RUBICON,
                givenBidRequest(identity()),
                null);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", null));
    }

    @Test
    public void revertAdjustmentForImpShouldReturnBidFloorNotFactoredByOtherBidder() {
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
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                givenImp(identity()),
                RUBICON,
                bidRequest,
                null);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void revertAdjustmentForImpShouldReturnFactoredOfOneIfExtBidAdjustmentsFactorMediaTypesIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidAdjustmentFactors.builder()
                                .mediatypes(null)
                                .build())
                        .build())));

        // when
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                givenImp(identity()),
                RUBICON,
                bidRequest,
                null);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void revertAdjustmentForImpShouldReturnFactorOfOneIfNoMediaTypeInImpression() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final Imp imp = givenImp(impBuilder -> impBuilder.video(null));

        // when
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                imp,
                RUBICON,
                bidRequest,
                null);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
    }

    @Test
    public void revertAdjustmentForImpShouldSkipMediaTypeIfNoMediaTypesOfImpFound() {
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
        final Price adjustedBidPrice = target.revertAdjustmentForImp(
                givenImp(identity()),
                RUBICON,
                bidRequest,
                null);

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", BigDecimal.TEN));
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
