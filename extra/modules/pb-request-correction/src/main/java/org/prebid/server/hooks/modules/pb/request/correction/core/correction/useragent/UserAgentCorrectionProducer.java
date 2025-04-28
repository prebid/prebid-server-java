package org.prebid.server.hooks.modules.pb.request.correction.core.correction.useragent;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.pb.request.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.Correction;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.CorrectionProducer;
import org.prebid.server.hooks.modules.pb.request.correction.core.util.VersionUtil;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserAgentCorrectionProducer implements CorrectionProducer {

    private static final UserAgentCorrection CORRECTION_INSTANCE = new UserAgentCorrection();

    private static final String PREBID_MOBILE = "prebid-mobile";
    private static final Pattern USER_AGENT_PATTERN = Pattern.compile(".*PrebidMobile/[0-9]+[^ ]*.*");

    private static final int MAX_VERSION_MAJOR = 2;
    private static final int MAX_VERSION_MINOR = 1;
    private static final int MAX_VERSION_PATCH = 6;

    @Override
    public boolean shouldProduce(Config config, BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        return config.isUserAgentCorrectionEnabled()
                && isPrebidMobile(app)
                && isApplicableVersion(app)
                && isApplicableDevice(bidRequest.getDevice());
    }

    private static boolean isPrebidMobile(App app) {
        final String source = Optional.ofNullable(app)
                .map(App::getExt)
                .map(ExtApp::getPrebid)
                .map(ExtAppPrebid::getSource)
                .orElse(null);

        return StringUtils.equalsIgnoreCase(source, PREBID_MOBILE);
    }

    private static boolean isApplicableVersion(App app) {
        return Optional.ofNullable(app)
                .map(App::getExt)
                .map(ExtApp::getPrebid)
                .map(ExtAppPrebid::getVersion)
                .map(UserAgentCorrectionProducer::checkVersion)
                .orElse(false);
    }

    private static boolean checkVersion(String version) {
        return VersionUtil.isVersionLessThan(version, MAX_VERSION_MAJOR, MAX_VERSION_MINOR, MAX_VERSION_PATCH);
    }

    private static boolean isApplicableDevice(Device device) {
        return Optional.ofNullable(device)
                .map(Device::getUa)
                .filter(StringUtils::isNotEmpty)
                .map(USER_AGENT_PATTERN::matcher)
                .map(Matcher::matches)
                .orElse(false);
    }

    @Override
    public Correction produce(Config config) {
        return CORRECTION_INSTANCE;
    }
}
