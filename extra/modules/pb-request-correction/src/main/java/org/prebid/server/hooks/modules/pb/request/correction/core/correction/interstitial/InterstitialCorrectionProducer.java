package org.prebid.server.hooks.modules.pb.request.correction.core.correction.interstitial;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.pb.request.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.Correction;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.CorrectionProducer;
import org.prebid.server.hooks.modules.pb.request.correction.core.util.VersionUtil;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;

import java.util.List;
import java.util.Optional;

public class InterstitialCorrectionProducer implements CorrectionProducer {

    private static final InterstitialCorrection CORRECTION_INSTANCE = new InterstitialCorrection();

    private static final String PREBID_MOBILE = "prebid-mobile";
    private static final String ANDROID = "android";

    private static final int MAX_VERSION_MAJOR = 2;
    private static final int MAX_VERSION_MINOR = 2;
    private static final int MAX_VERSION_PATCH = 3;

    @Override
    public boolean shouldProduce(Config config, BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        return config.isInterstitialCorrectionEnabled()
                && hasInterstitialToRemove(bidRequest.getImp())
                && isPrebidMobile(app)
                && isAndroid(app)
                && isApplicableVersion(app);
    }

    private static boolean hasInterstitialToRemove(List<Imp> imps) {
        for (Imp imp : imps) {
            final Integer interstitial = imp.getInstl();
            if (interstitial != null && interstitial == 1) {
                return true;
            }
        }

        return false;
    }

    private static boolean isPrebidMobile(App app) {
        final String source = Optional.ofNullable(app)
                .map(App::getExt)
                .map(ExtApp::getPrebid)
                .map(ExtAppPrebid::getSource)
                .orElse(null);

        return StringUtils.equalsIgnoreCase(source, PREBID_MOBILE);
    }

    private static boolean isAndroid(App app) {
        return StringUtils.containsIgnoreCase(app.getBundle(), ANDROID);
    }

    private static boolean isApplicableVersion(App app) {
        return Optional.ofNullable(app)
                .map(App::getExt)
                .map(ExtApp::getPrebid)
                .map(ExtAppPrebid::getVersion)
                .map(InterstitialCorrectionProducer::checkVersion)
                .orElse(false);
    }

    private static boolean checkVersion(String version) {
        return VersionUtil.isVersionLessThan(version, MAX_VERSION_MAJOR, MAX_VERSION_MINOR, MAX_VERSION_PATCH);
    }

    @Override
    public Correction produce() {
        return CORRECTION_INSTANCE;
    }
}
