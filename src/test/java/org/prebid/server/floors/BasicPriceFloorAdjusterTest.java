package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidadjustments.FloorAdjustmentFactorResolver;
import org.prebid.server.bidadjustments.FloorAdjustmentsResolver;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.exception.PreBidException;
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
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.proto.openrtb.ext.request.ImpMediaType.video_instream;

@ExtendWith(MockitoExtension.class)
public class BasicPriceFloorAdjusterTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    @Mock(strictness = LENIENT)
    private FloorAdjustmentFactorResolver floorAdjustmentFactorResolver;

    @Mock(strictness = LENIENT)
    private FloorAdjustmentsResolver floorAdjustmentsResolver;

    private BasicPriceFloorAdjuster target;

    @BeforeEach
    public void setUp() {
        given(floorAdjustmentFactorResolver.resolve(anySet(), any(), any())).willReturn(BigDecimal.ONE);
        given(floorAdjustmentsResolver.resolve(any(), any(), anySet(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        target = new BasicPriceFloorAdjuster(floorAdjustmentFactorResolver, floorAdjustmentsResolver);
    }

    @Test
    public void adjustForImpShouldApplyAllAdjustments() {
        // given
        given(floorAdjustmentFactorResolver.resolve(anySet(), any(), any())).willReturn(new BigDecimal("0.1"));
        given(floorAdjustmentsResolver.resolve(any(), any(), anySet(), any()))
                .willReturn(Price.of("UAH", new BigDecimal("117.00")));
        final BidRequest givenBidRequest = givenBidRequest(identity());

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                givenImp(identity()),
                RUBICON,
                givenBidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("UAH", new BigDecimal("117.00")));
        verify(floorAdjustmentFactorResolver).resolve(eq(Set.of(video_instream)), any(), eq(RUBICON));
        verify(floorAdjustmentsResolver).resolve(
                eq(Price.of("USD", new BigDecimal(100))),
                eq(givenBidRequest),
                eq(Set.of(video_instream)),
                eq(RUBICON));
    }

    @Test
    public void adjustForImpShouldNotApplyAdjustmentsWhenAdjustmentDisabledByAccount() {
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
        verifyNoInteractions(floorAdjustmentsResolver, floorAdjustmentFactorResolver);
    }

    @Test
    public void adjustForImpShouldNotApplyAdjustmentsWhenAdjustmentDisabledByRequest() {
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
        verifyNoInteractions(floorAdjustmentsResolver, floorAdjustmentFactorResolver);
    }

    @Test
    public void adjustForImpShouldApplyNoFactorAdjustmentsWhenBidAdjustmentsFactorIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder().bidadjustmentfactors(null).build())));
        given(floorAdjustmentsResolver.resolve(any(), any(), anySet(), any()))
                .willReturn(Price.of("UAH", new BigDecimal("117.00")));
        final Imp imp = givenImp(identity());

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                imp,
                RUBICON,
                bidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("UAH", new BigDecimal("117.00")));
        verifyNoInteractions(floorAdjustmentFactorResolver);
        verify(floorAdjustmentsResolver).resolve(
                eq(Price.of(imp.getBidfloorcur(), imp.getBidfloor())),
                eq(bidRequest),
                eq(Set.of(video_instream)),
                eq(RUBICON));
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
        verifyNoInteractions(floorAdjustmentFactorResolver, floorAdjustmentsResolver);
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
    public void adjustForImpShouldSkipBidAdjustmentsWhenResolverThrowsException() {
        // given
        given(floorAdjustmentFactorResolver.resolve(anySet(), any(), any())).willReturn(new BigDecimal("0.1"));
        given(floorAdjustmentsResolver.resolve(any(), any(), anySet(), any()))
                .willThrow(new PreBidException("Exception"));
        final BidRequest givenBidRequest = givenBidRequest(identity());

        // when
        final Price adjustedBidPrice = target.adjustForImp(
                givenImp(identity()),
                RUBICON,
                givenBidRequest,
                null,
                new ArrayList<>());

        // then
        assertThat(adjustedBidPrice).isEqualTo(Price.of("USD", new BigDecimal(100)));
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
