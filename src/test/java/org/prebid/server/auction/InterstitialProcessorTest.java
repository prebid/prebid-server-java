package org.prebid.server.auction;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InterstitialProcessorTest extends VertxTest {

    private InterstitialProcessor interstitialProcessor = new InterstitialProcessor();

    @Test
    public void processShouldReturnBidRequestUpdatedWithImpsInterstitialFormat() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder()
                        .format(singletonList(Format.builder().w(400).h(600).build())).build()).instl(1)
                        .build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .containsOnly(Format.builder().w(320).h(480).build(),
                        Format.builder().w(336).h(544).build(),
                        Format.builder().w(320).h(568).build(),
                        Format.builder().w(320).h(500).build(),
                        Format.builder().w(320).h(481).build());
    }

    @Test
    public void processShouldReturnBidRequestUpdatedWithInterstitialFormatsUsingSizesFromDeviceWhenFormatIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build()).instl(1).build()))
                .device(Device.builder().w(400).h(600)
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .containsOnly(Format.builder().w(320).h(480).build(),
                        Format.builder().w(336).h(544).build(),
                        Format.builder().w(320).h(568).build(),
                        Format.builder().w(320).h(500).build(),
                        Format.builder().w(320).h(481).build());
    }

    @Test
    public void processShouldReturnBidRequestUpdatedWithInterstitialFormatsUsingSizesFromDeviceWhenFormatIsOneToOne() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().format(singletonList(
                        Format.builder().w(1).h(1).build())).build()).instl(1).build()))
                .device(Device.builder().w(400).h(600)
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .containsOnly(Format.builder().w(320).h(480).build(),
                        Format.builder().w(336).h(544).build(),
                        Format.builder().w(320).h(568).build(),
                        Format.builder().w(320).h(500).build(),
                        Format.builder().w(320).h(481).build());
    }

    @Test
    public void processShouldReturnBidRequestUpdatedWithInterstitialFormatsLimitedByTen() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder()
                        .format(singletonList(Format.builder().w(400).h(600).build())).build()).instl(1)
                        .build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(1, 1))))).build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .containsOnly(Format.builder().w(300).h(250).build(),
                        Format.builder().w(160).h(600).build(),
                        Format.builder().w(320).h(50).build(),
                        Format.builder().w(300).h(600).build(),
                        Format.builder().w(320).h(320).build(),
                        Format.builder().w(120).h(600).build(),
                        Format.builder().w(300).h(50).build(),
                        Format.builder().w(336).h(280).build(),
                        Format.builder().w(320).h(250).build(),
                        Format.builder().w(320).h(480).build());
    }

    @Test
    public void processShouldNotUpdateBidRequestWhenImpInstlIsEqualToZero() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build()).instl(0).build()))
                .device(Device.builder().w(400).h(600)
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result).isSameAs(bidRequest);
    }

    @Test
    public void processShouldNotUpdateBidRequestWhenExtDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build()).instl(1).build()))
                .device(Device.builder().build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result).isSameAs(bidRequest);
    }

    @Test
    public void processShouldNotUpdateImpWithInstlZeroAndUpdateImpWithIstlOne() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(600).build()))
                                .build()).instl(0).build(),
                        Imp.builder().banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(600).build()))
                                .build()).instl(1).build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result).isEqualTo(BidRequest.builder()
                .imp(asList(
                        Imp.builder().banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(600).build()))
                                .build()).instl(0).build(),
                        Imp.builder().banner(Banner.builder()
                                .format(asList(Format.builder().w(320).h(480).build(),
                                        Format.builder().w(336).h(544).build(),
                                        Format.builder().w(320).h(568).build(),
                                        Format.builder().w(320).h(500).build(),
                                        Format.builder().w(320).h(481).build()))
                                .build()).instl(1).build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build());
    }

    @Test
    public void processShouldNotUpdateImpWithoutBanner() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().instl(1).build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result).isEqualTo(BidRequest.builder()
                .imp(singletonList(Imp.builder().instl(1).build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build());
    }

    @Test
    public void processShouldThrowInvalidRequestExceptionWhenCantFindMaxWidthOrMaxHeight() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder().id("impId").banner(Banner.builder()
                                .format(singletonList(Format.builder().w(1).h(1).build()))
                                .build()).instl(1).build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build();

        // when and then
        assertThatThrownBy(() -> interstitialProcessor.process(bidRequest))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessageEndingWith(
                        "Unable to read max interstitial size for Imp id=impId (No Device sizes and no Format objects)");
    }

    @Test
    public void processShouldNotUpdateImpWhenInterstitialSizesWereNotFound() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder().banner(Banner.builder()
                                .format(singletonList(Format.builder().w(10).h(10).build()))
                                .build()).instl(1).build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build();

        // when
        final BidRequest result = interstitialProcessor.process(bidRequest);

        // then
        assertThat(result).isEqualTo(BidRequest.builder()
                .imp(singletonList(
                        Imp.builder().banner(Banner.builder()
                                .format(singletonList(Format.builder().w(10).h(10).build()))
                                .build()).instl(1).build()))
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(80, 80))))).build())
                .build());
    }
}
