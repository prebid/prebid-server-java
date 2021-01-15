package org.prebid.server.auction;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class InterstitialProcessor {

    private static final int MAX_SIZES_COUNT = 10;

    public BidRequest process(BidRequest bidRequest) {
        if (bidRequest.getImp().stream().anyMatch(this::isInterstitial)) {
            bidRequest = processBidRequest(bidRequest);
        }
        return bidRequest;
    }

    private boolean isInterstitial(Imp imp) {
        return Objects.equals(imp.getInstl(), 1);
    }

    private BidRequest processBidRequest(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        final ExtDeviceInt extDeviceInt = getExtDeviceInt(device);
        if (extDeviceInt != null) {
            final int minWidthPerc = extDeviceInt.getMinWidthPerc();
            final int minHeightPerc = extDeviceInt.getMinHeightPerc();
            final List<Imp> updatedImps = bidRequest.getImp().stream()
                    .map(imp -> processInterstitialImp(imp, device, minWidthPerc, minHeightPerc))
                    .collect(Collectors.toList());
            bidRequest = bidRequest.toBuilder().imp(updatedImps).build();
        }
        return bidRequest;
    }

    private Imp processInterstitialImp(Imp imp, Device device, int minWidthPerc, int minHeightPerc) {
        if (!isInterstitial(imp)) {
            return imp;
        }

        final Banner banner = imp.getBanner();
        if (banner == null) {
            return imp;
        }

        final List<Format> formats = banner.getFormat();
        final Format firstFormat = CollectionUtils.isEmpty(formats) ? null : formats.get(0);
        Integer maxHeight = firstFormat != null ? firstFormat.getH() : null;
        Integer maxWidth = firstFormat != null ? firstFormat.getW() : null;

        if (maxHeight == null || maxWidth == null || (maxHeight == 1 && maxWidth == 1)) {
            maxHeight = device.getH();
            maxWidth = device.getW();
        }

        if (maxHeight == null || maxWidth == null) {
            throw new InvalidRequestException(String.format(
                    "Unable to read max interstitial size for Imp id=%s (No Device sizes and no Format objects)",
                    imp.getId()));
        }

        final double minHeight = (double) maxHeight / 100 * minHeightPerc;
        final double minWidth = (double) maxWidth / 100 * minWidthPerc;

        final List<Format> interstitialFormats =
                InterstitialSize.getNestedSizes(minWidth, minHeight, maxWidth, maxHeight, MAX_SIZES_COUNT)
                        .stream()
                        .map(interstitialSize -> Format.builder().w(interstitialSize.w).h(interstitialSize.h).build())
                        .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(interstitialFormats)) {
            return imp;
        }

        return imp.toBuilder().banner(banner.toBuilder().format(interstitialFormats).build()).build();
    }

    private ExtDeviceInt getExtDeviceInt(Device device) {
        final ExtDevice extDevice = device != null ? device.getExt() : null;
        final ExtDevicePrebid extDevicePrebid = extDevice != null ? extDevice.getPrebid() : null;
        return extDevicePrebid != null ? extDevicePrebid.getInterstitial() : null;
    }

    private static class InterstitialSize {
        private static final List<InterstitialSize> INTERSTITIAL_SIZES = new ArrayList<>();

        static {
            INTERSTITIAL_SIZES.add(interstitialSize(300, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(728, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(160, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 50));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(2000, 1400));
            INTERSTITIAL_SIZES.add(interstitialSize(1920, 1200));
            INTERSTITIAL_SIZES.add(interstitialSize(1800, 1000));
            INTERSTITIAL_SIZES.add(interstitialSize(1920, 1080));
            INTERSTITIAL_SIZES.add(interstitialSize(1600, 1150));
            INTERSTITIAL_SIZES.add(interstitialSize(1696, 900));
            INTERSTITIAL_SIZES.add(interstitialSize(1600, 900));
            INTERSTITIAL_SIZES.add(interstitialSize(1270, 800));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 1000));
            INTERSTITIAL_SIZES.add(interstitialSize(1920, 480));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 320));
            INTERSTITIAL_SIZES.add(interstitialSize(1600, 500));
            INTERSTITIAL_SIZES.add(interstitialSize(768, 1024));
            INTERSTITIAL_SIZES.add(interstitialSize(1024, 768));
            INTERSTITIAL_SIZES.add(interstitialSize(828, 910));
            INTERSTITIAL_SIZES.add(interstitialSize(728, 970));
            INTERSTITIAL_SIZES.add(interstitialSize(120, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(640, 960));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(620, 891));
            INTERSTITIAL_SIZES.add(interstitialSize(930, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 552));
            INTERSTITIAL_SIZES.add(interstitialSize(1272, 328));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 50));
            INTERSTITIAL_SIZES.add(interstitialSize(500, 1000));
            INTERSTITIAL_SIZES.add(interstitialSize(900, 550));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 500));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 500));
            INTERSTITIAL_SIZES.add(interstitialSize(800, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(336, 280));
            INTERSTITIAL_SIZES.add(interstitialSize(1250, 360));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 400));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 480));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 240));
            INTERSTITIAL_SIZES.add(interstitialSize(580, 400));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 415));
            INTERSTITIAL_SIZES.add(interstitialSize(480, 820));
            INTERSTITIAL_SIZES.add(interstitialSize(620, 620));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(600, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(1800, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 310));
            INTERSTITIAL_SIZES.add(interstitialSize(720, 480));
            INTERSTITIAL_SIZES.add(interstitialSize(1295, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 1050));
            INTERSTITIAL_SIZES.add(interstitialSize(1272, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(640, 480));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(580, 500));
            INTERSTITIAL_SIZES.add(interstitialSize(1000, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(1250, 240));
            INTERSTITIAL_SIZES.add(interstitialSize(600, 500));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 1000));
            INTERSTITIAL_SIZES.add(interstitialSize(728, 410));
            INTERSTITIAL_SIZES.add(interstitialSize(800, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(950, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(994, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(940, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(640, 320));
            INTERSTITIAL_SIZES.add(interstitialSize(468, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(930, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(250, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(491, 555));
            INTERSTITIAL_SIZES.add(interstitialSize(550, 480));
            INTERSTITIAL_SIZES.add(interstitialSize(750, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(1000, 260));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(350, 240));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 210));
            INTERSTITIAL_SIZES.add(interstitialSize(640, 360));
            INTERSTITIAL_SIZES.add(interstitialSize(580, 415));
            INTERSTITIAL_SIZES.add(interstitialSize(480, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(750, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(360, 640));
            INTERSTITIAL_SIZES.add(interstitialSize(624, 368));
            INTERSTITIAL_SIZES.add(interstitialSize(900, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(468, 400));
            INTERSTITIAL_SIZES.add(interstitialSize(608, 226));
            INTERSTITIAL_SIZES.add(interstitialSize(690, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(605, 340));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 640));
            INTERSTITIAL_SIZES.add(interstitialSize(450, 450));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 480));
            INTERSTITIAL_SIZES.add(interstitialSize(250, 800));
            INTERSTITIAL_SIZES.add(interstitialSize(640, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 160));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(950, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(480, 400));
            INTERSTITIAL_SIZES.add(interstitialSize(740, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(336, 544));
            INTERSTITIAL_SIZES.add(interstitialSize(303, 603));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 568));
            INTERSTITIAL_SIZES.add(interstitialSize(301, 601));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 601));
            INTERSTITIAL_SIZES.add(interstitialSize(600, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(180, 500));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 120));
            INTERSTITIAL_SIZES.add(interstitialSize(950, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(935, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(994, 170));
            INTERSTITIAL_SIZES.add(interstitialSize(468, 360));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 400));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 240));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 500));
            INTERSTITIAL_SIZES.add(interstitialSize(316, 513));
            INTERSTITIAL_SIZES.add(interstitialSize(630, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(480, 320));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 481));
            INTERSTITIAL_SIZES.add(interstitialSize(520, 290));
            INTERSTITIAL_SIZES.add(interstitialSize(250, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 500));
            INTERSTITIAL_SIZES.add(interstitialSize(1000, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 460));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(800, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(468, 60));
            INTERSTITIAL_SIZES.add(interstitialSize(482, 282));
            INTERSTITIAL_SIZES.add(interstitialSize(680, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 416));
            INTERSTITIAL_SIZES.add(interstitialSize(480, 280));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 431));
            INTERSTITIAL_SIZES.add(interstitialSize(728, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 430));
            INTERSTITIAL_SIZES.add(interstitialSize(180, 701));
            INTERSTITIAL_SIZES.add(interstitialSize(840, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(600, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(768, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(200, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(350, 350));
            INTERSTITIAL_SIZES.add(interstitialSize(202, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(400, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(414, 286));
            INTERSTITIAL_SIZES.add(interstitialSize(656, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(994, 118));
            INTERSTITIAL_SIZES.add(interstitialSize(638, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(650, 170));
            INTERSTITIAL_SIZES.add(interstitialSize(1000, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 360));
            INTERSTITIAL_SIZES.add(interstitialSize(600, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(240, 400));
            INTERSTITIAL_SIZES.add(interstitialSize(161, 601));
            INTERSTITIAL_SIZES.add(interstitialSize(610, 138));
            INTERSTITIAL_SIZES.add(interstitialSize(164, 601));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(468, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(250, 360));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(605, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(600, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(980, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(750, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(150, 600));
            INTERSTITIAL_SIZES.add(interstitialSize(630, 140));
            INTERSTITIAL_SIZES.add(interstitialSize(696, 120));
            INTERSTITIAL_SIZES.add(interstitialSize(307, 254));
            INTERSTITIAL_SIZES.add(interstitialSize(303, 253));
            INTERSTITIAL_SIZES.add(interstitialSize(703, 110));
            INTERSTITIAL_SIZES.add(interstitialSize(550, 140));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 251));
            INTERSTITIAL_SIZES.add(interstitialSize(298, 250));
            INTERSTITIAL_SIZES.add(interstitialSize(500, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(413, 180));
            INTERSTITIAL_SIZES.add(interstitialSize(728, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(269, 269));
            INTERSTITIAL_SIZES.add(interstitialSize(640, 106));
            INTERSTITIAL_SIZES.add(interstitialSize(768, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(728, 93));
            INTERSTITIAL_SIZES.add(interstitialSize(729, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(727, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(640, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(720, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(970, 66));
            INTERSTITIAL_SIZES.add(interstitialSize(480, 110));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(707, 83));
            INTERSTITIAL_SIZES.add(interstitialSize(900, 65));
            INTERSTITIAL_SIZES.add(interstitialSize(467, 120));
            INTERSTITIAL_SIZES.add(interstitialSize(200, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(450, 121));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(600, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 170));
            INTERSTITIAL_SIZES.add(interstitialSize(468, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 169));
            INTERSTITIAL_SIZES.add(interstitialSize(500, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(990, 50));
            INTERSTITIAL_SIZES.add(interstitialSize(140, 350));
            INTERSTITIAL_SIZES.add(interstitialSize(160, 300));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 158));
            INTERSTITIAL_SIZES.add(interstitialSize(190, 240));
            INTERSTITIAL_SIZES.add(interstitialSize(180, 150));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 145));
            INTERSTITIAL_SIZES.add(interstitialSize(310, 122));
            INTERSTITIAL_SIZES.add(interstitialSize(468, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(594, 70));
            INTERSTITIAL_SIZES.add(interstitialSize(480, 80));
            INTERSTITIAL_SIZES.add(interstitialSize(600, 65));
            INTERSTITIAL_SIZES.add(interstitialSize(484, 80));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 75));
            INTERSTITIAL_SIZES.add(interstitialSize(335, 100));
            INTERSTITIAL_SIZES.add(interstitialSize(375, 80));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 75));
            INTERSTITIAL_SIZES.add(interstitialSize(120, 240));
            INTERSTITIAL_SIZES.add(interstitialSize(480, 60));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(120, 60));
            INTERSTITIAL_SIZES.add(interstitialSize(100, 200));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 80));
            INTERSTITIAL_SIZES.add(interstitialSize(160, 160));
            INTERSTITIAL_SIZES.add(interstitialSize(400, 63));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 81));
            INTERSTITIAL_SIZES.add(interstitialSize(1, 1));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 80));
            INTERSTITIAL_SIZES.add(interstitialSize(375, 58));
            INTERSTITIAL_SIZES.add(interstitialSize(232, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(321, 51));
            INTERSTITIAL_SIZES.add(interstitialSize(320, 63));
            INTERSTITIAL_SIZES.add(interstitialSize(319, 49));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 65));
            INTERSTITIAL_SIZES.add(interstitialSize(360, 50));
            INTERSTITIAL_SIZES.add(interstitialSize(125, 125));
            INTERSTITIAL_SIZES.add(interstitialSize(298, 60));
            INTERSTITIAL_SIZES.add(interstitialSize(300, 60));
            INTERSTITIAL_SIZES.add(interstitialSize(299, 60));
            INTERSTITIAL_SIZES.add(interstitialSize(301, 50));
            INTERSTITIAL_SIZES.add(interstitialSize(234, 60));
            INTERSTITIAL_SIZES.add(interstitialSize(280, 47));
            INTERSTITIAL_SIZES.add(interstitialSize(120, 90));
            INTERSTITIAL_SIZES.add(interstitialSize(13, 13));
            INTERSTITIAL_SIZES.add(interstitialSize(17, 17));
            INTERSTITIAL_SIZES.add(interstitialSize(168, 50));
            INTERSTITIAL_SIZES.add(interstitialSize(140, 50));
            INTERSTITIAL_SIZES.add(interstitialSize(120, 20));
        }

        private final Integer w;
        private final Integer h;

        private InterstitialSize(Integer w, Integer h) {
            this.w = w;
            this.h = h;
        }

        private static InterstitialSize interstitialSize(Integer w, Integer h) {
            return new InterstitialSize(w, h);
        }

        private static List<InterstitialSize> getNestedSizes(double minWidth, double minHeight, double maxWidth,
                                                             double maxHeight, int count) {
            return INTERSTITIAL_SIZES.stream()
                    .filter(size -> isNested(size, minWidth, minHeight, maxWidth, maxHeight))
                    .limit(count)
                    .collect(Collectors.toList());
        }

        private static boolean isNested(InterstitialSize size, double minWidth, double minHeight, double maxWidth,
                                        double maxHeight) {
            return size.w >= minWidth && size.w <= maxWidth && size.h >= minHeight && size.h <= maxHeight;
        }
    }
}
