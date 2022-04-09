package org.prebid.server.floors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.PriceFloorField;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.model.PriceFloorSchema;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BasicPriceFloorResolverTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CurrencyConversionService currencyConversionService;
    @Mock
    private CountryCodeMapper countryCodeMapper;
    @Mock
    private Metrics metrics;

    private BasicPriceFloorResolver priceFloorResolver;

    @Before
    public void setUp() {
        priceFloorResolver = new BasicPriceFloorResolver(currencyConversionService, countryCodeMapper, metrics);
    }

    @Test
    public void resolveShouldReturnNullWhenModelGroupIsNotPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest, null, Imp.builder().build(), null)).isNull();
    }

    @Test
    public void resolveShouldReturnNullWhenNoModelGroupSchema() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest, PriceFloorModelGroup.builder().build(),
                Imp.builder().build(), null)).isNull();
    }

    @Test
    public void resolveShouldReturnNullWhenModelGroupSchemaFieldsIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", emptyList()))
                        .build(),
                Imp.builder().build(), null)).isNull();
    }

    @Test
    public void resolveShouldReturnNullWhenNoModelGroupSchemaFields() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", null))
                        .build(),
                Imp.builder().build(), null)).isNull();
    }

    @Test
    public void resolveShouldReturnNullWhenModelGroupValuesIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.channel)))
                        .values(emptyMap())
                        .build(),
                Imp.builder().build(), null)).isNull();
    }

    @Test
    public void resolveShouldReturnNullWhenNoModelGroupValues() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.channel)))
                        .build(),
                Imp.builder().build(), null)).isNull();
    }

    @Test
    public void resolveShouldReturnNullWhenNoSiteDomainPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.siteDomain)))
                        .value("siteDomain", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorForSiteDomainPresentedBySite() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("siteDomain").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.siteDomain)))
                        .value("siteDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForSiteDomainPresentedByApp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().domain("appDomain").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.siteDomain)))
                        .value("appDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenNoPubDomainPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pubDomain)))
                        .value("pubDomain", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorForPubDomainPresentedBySite() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().domain("siteDomain").build())
                        .build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pubDomain)))
                        .value("siteDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForPubDomainPresentedByApp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().domain("appDomain").build())
                        .build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pubDomain)))
                        .value("appDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenNoDomainPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.domain)))
                        .value("pubDomain", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorForDomainPresentedBySite() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("siteDomain").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.domain)))
                        .value("siteDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForDomainPresentedByApp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().domain("appDomain").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.domain)))
                        .value("appDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForDomainPresentedBySitePublisher() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().domain("siteDomain").build())
                        .build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.domain)))
                        .value("siteDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForDomainPresentedByAppPublisher() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().domain("appDomain").build())
                        .build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.domain)))
                        .value("appDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenNoBundlePresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.bundle)))
                        .value("bundle", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorIfBundlePresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .bundle("someBundle")
                        .build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.bundle)))
                        .value("someBundle", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenNoChannelPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.channel)))
                        .value("channel", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorIfChannelPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .bundle("someBundle")
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .channel(ExtRequestPrebidChannel.of("someChannelName"))
                        .build()))
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.channel)))
                        .value("someChannelName", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenMediaTypeDoesNotMatchRuleMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                        .value("video", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorForCatchAllImpMediaTypeWhenImpContainsMoreThanOneType() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                        .value("banner", BigDecimal.ONE)
                        .value("*", BigDecimal.TEN)
                        .value("video", BigDecimal.ONE)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .video(Video.builder().build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenImpMediaTypeIsBannerAndRuleMediaTypeIsBanner() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                        .value("banner", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenImpMediaTypeIsVideoInStreamAndRuleMediaTypeIsVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                        .value("video", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().placement(1).build())),
                null
        ).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenImpMediaTypeIsVideoByEmptyPlacementInStreamAndRuleMediaTypeIsVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                        .value("video", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().placement(null).build())), null
        ).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenImpMediaTypeIsVideoInStreamAndRuleMediaTypeIsVideoInstream() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                        .value("video-instream", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().placement(1).build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenImpMediaTypeIsNativeAndRuleMediaTypeIsNative() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                        .value("native", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(null)
                        .xNative(Native.builder().build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenImpMediaTypeIsAudioAndRuleMediaTypeIsAudio() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                        .value("native", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(null)
                        .xNative(Native.builder().build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenSizeDoesNotMatchRuleSize() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.size)))
                        .value("250x300", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenMediaTypeIsBannerAndTakePriorityForFormatInSizeParameter() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.size)))
                        .value("100x150", BigDecimal.ONE)
                        .value("250x300", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .w(100)
                                .h(150)
                                .format(singletonList(Format.builder().w(250).h(300).build()))
                                .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForCatchAllWildcardWhenMultipleFormats() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.size)))
                        .value("400x500", BigDecimal.ONE)
                        .value("*", BigDecimal.TEN)
                        .value("250x300", BigDecimal.ONE)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .w(100)
                                .h(150)
                                .format(asList(Format.builder().w(250).h(300).build(),
                                        Format.builder().w(400).h(500).build()))
                                .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenMediaTypeIsBannerAndTakeSizesForFormatInSizeParameter() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.size)))
                        .value("250x300", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .w(250)
                                .h(300)
                                .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenMediaTypeIsVideoAndTakeSizesForFormatInSizeParameter() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.size)))
                        .value("250x300", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder()
                                .w(250)
                                .h(300)
                                .placement(1)
                                .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenGptSlotDoesNotMatchRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.gptSlot)))
                        .value("someGptSlot", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorIfAdserverNameIsGamAndAdSlotMatchesRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final ObjectNode impExt = mapper.createObjectNode();
        final ObjectNode adServerNode = mapper.createObjectNode();
        adServerNode.put("name", "gam");
        adServerNode.put("adslot", "someGptSlot");
        final ObjectNode dataNode = mapper.createObjectNode();
        dataNode.set("adserver", adServerNode);
        impExt.set("data", dataNode);

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.gptSlot)))
                        .value("someGptSlot", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder.ext(impExt)), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullIfAdserverNameIsNotGamAndAdSlotMatchesRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final ObjectNode impExt = mapper.createObjectNode();
        final ObjectNode adServerNode = mapper.createObjectNode();
        adServerNode.put("name", "notGam");
        adServerNode.put("adslot", "someGptSlot");
        final ObjectNode dataNode = mapper.createObjectNode();
        dataNode.set("adserver", adServerNode);
        impExt.set("data", dataNode);

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.gptSlot)))
                        .value("someGptSlot", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder.ext(impExt)), null))
                .isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorFromPbAdSlotIfAdserverNameIsNotGamAndAdSlotMatchesRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final ObjectNode impExt = mapper.createObjectNode();
        final ObjectNode dataNode = mapper.createObjectNode();
        dataNode.put("pbadslot", "somePbAdSlot");
        impExt.set("data", dataNode);

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.gptSlot)))
                        .value("somePbAdSlot", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder.ext(impExt)), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenPbAdSlotDoesNotMatchRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pbAdSlot)))
                        .value("somePbAdSlot", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorIfPbAdSlotMatchesRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final ObjectNode impExt = mapper.createObjectNode();
        final ObjectNode dataNode = mapper.createObjectNode();
        dataNode.put("pbadslot", "somePbAdSlot");
        impExt.set("data", dataNode);

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pbAdSlot)))
                        .value("somePbAdSlot", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder.ext(impExt)), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenCountryDoesNotMatchRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.country)))
                        .value("USA", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenCountryMatchesRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country("USA").build()).build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.country)))
                        .value("usa", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorWhenCountryAlpha3IsForMatchingRuleAlpha2() {
        // given
        given(countryCodeMapper.mapToAlpha3("US")).willReturn("USA");
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country("US").build()).build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.country)))
                        .value("usa", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnNullWhenDeviceTypeDoesNotMatchRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("desktop", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null)).isNull();
    }

    @Test
    public void resolveShouldReturnPriceFloorForPhoneTypeWhenUaMatchesPhone() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("Phone").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("phone", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForPhoneTypeWhenUaMatchesIPhone() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("iPhone").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("phone", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForPhoneTypeWhenUaMatchesAndroidMobile() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("Android. SomeMobile").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("phone", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForPhoneTypeWhenUaMatchesMobileAndroid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("Mobile. Some Android").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("phone", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForTabletTypeWhenUaMatchesTablet() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("tablet").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("tablet", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForTabletTypeWhenUaMatchesIPad() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("iPad").build())
                .build();

        // when and then
        final BigDecimal floorValue = priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("tablet", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue();
        assertThat(floorValue)
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForTabletTypeWhenUaMatchesWindowsNt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("Windows NT. some touch").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("tablet", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForTabletTypeWhenUaMatchesAndroid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("Android").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("tablet", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnPriceFloorForDesktopTypeWhenUaDoesNotMatchTabletOrPhoneTypes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.deviceType)))
                        .value("desktop", BigDecimal.TEN)
                        .build(),
                givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnFloorWhenAllFieldsMatchExactly() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|",
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("desktop|*|300x250", BigDecimal.ONE)
                        .value("*|banner|300x250", BigDecimal.ONE)
                        .value("desktop|banner|300x250", BigDecimal.TEN)
                        .value("desktop|banner|*", BigDecimal.ONE)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnFloorWhenMostAccurateWildcardMatchIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|",
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("desktop|*|300x250", BigDecimal.ONE)
                        .value("desktop|banner|*", BigDecimal.TEN)
                        .value("*|banner|300x250", BigDecimal.ONE)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnFloorWhenNarrowerWildcardMatchIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|",
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("desktop|*|*", BigDecimal.ONE)
                        .value("*|banner|300x250", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnFloorWhenCaseIsDifferent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|",
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("deSKtop|baNNer|300X250", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnFloorWhenDelimiterIsNullAndDefaultAssumed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of(null,
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("desktop|banner|300x250", BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnDefaultWhenNoMatchingRule() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of(null,
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("video|banner|300x250", BigDecimal.ONE)
                        .defaultFloor(BigDecimal.TEN)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnFloorInRulesCurrency() {
        // given
        given(currencyConversionService.convertCurrency(eq(BigDecimal.ONE), any(), eq("EUR"), eq("USD")))
                .willReturn(BigDecimal.TEN);
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .currency("EUR")
                        .schema(PriceFloorSchema.of("|",
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("desktop|banner|300x250", BigDecimal.ONE)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null))
                .isEqualTo(PriceFloorResult.of("desktop|banner|300x250", BigDecimal.ONE, BigDecimal.ONE, "EUR"));
    }

    @Test
    public void resolveShouldReturnFloorInRulesCurrencyIfConversionIsNotPossible() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .willThrow(new PreBidException("Some message"));
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .floors(PriceFloorRules.builder()
                                        .floorMin(BigDecimal.ONE)
                                        .floorMinCur("UNKNOWN")
                                        .build())
                        .build()))
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .currency("EUR")
                        .schema(PriceFloorSchema.of("|",
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("desktop|banner|300x250", BigDecimal.ONE)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null))
                .isNull();
        verify(metrics).updatePriceFloorGeneralAlertsMetric(MetricName.err);
    }

    @Test
    public void resolveShouldReturnFloorRuleThatWasSelected() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("potential desktop type").build())
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|",
                                List.of(PriceFloorField.deviceType, PriceFloorField.mediaType, PriceFloorField.size)))
                        .value("desktop|banner|300x250", BigDecimal.ONE)
                        .build(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .build())), null).getFloorRule())
                .isEqualTo("desktop|banner|300x250");
    }

    @Test
    public void resolveShouldReturnEffectiveFloorMinIfCurrencyIsTheSameAndAllFloorsResolved() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().domain("appDomain").build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .floors(PriceFloorRules.builder()
                                .floorMin(BigDecimal.TEN)
                                .floorMinCur("USD")
                                .build())
                        .build()))
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pubDomain)))
                        .value("appDomain", BigDecimal.TEN)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void resolveShouldReturnConvertedFloorMinInProvidedCurrencyAndFloorMinMoreThanFloor() {
        // given
        when(currencyConversionService.convertCurrency(any(), any(), eq("EUR"), eq("GUF")))
                .thenReturn(BigDecimal.TEN);

        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().domain("appDomain").build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .floors(PriceFloorRules.builder()
                                .floorMin(BigDecimal.ONE)
                                .floorMinCur("EUR")
                                .build())
                        .build()))
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pubDomain)))
                        .currency("GUF")
                        .value("appDomain", BigDecimal.valueOf(5))
                        .build(), givenImp(identity()), null))
                .isEqualTo(PriceFloorResult.of("appdomain", BigDecimal.valueOf(5), BigDecimal.TEN, "GUF"));
    }

    @Test
    public void resolveShouldReturnCorrectValueAfterRoundingUpFifthDecimalNumber() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().domain("appDomain").build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .floors(PriceFloorRules.builder()
                                .floorMin(BigDecimal.valueOf(9.00009D))
                                .floorMinCur("USD")
                                .build())
                        .build()))
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pubDomain)))
                        .value("appDomain", BigDecimal.ZERO)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.valueOf(9.0001D));
    }

    @Test
    public void resolveShouldReturnCorrectValueAfterRoundingUpToWhole() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().domain("appDomain").build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .floors(PriceFloorRules.builder()
                                .floorMin(BigDecimal.valueOf(9.99999D))
                                .floorMinCur("USD")
                                .build())
                        .build()))
                .build();

        // when and then
        assertThat(priceFloorResolver.resolve(bidRequest,
                PriceFloorModelGroup.builder()
                        .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.pubDomain)))
                        .value("appDomain", BigDecimal.ZERO)
                        .build(), givenImp(identity()), null).getFloorValue())
                .isEqualTo(BigDecimal.TEN);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build()))
                .build();
    }
}
