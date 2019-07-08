package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.sharethrough.model.Size;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.sharethrough.ExtImpSharethrough;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;

class SharethroughRequestUtil {

    private static final int MIN_CHROME_VERSION = 53;
    private static final int MIN_SAFARI_VERSION = 10;

    private SharethroughRequestUtil() {
    }

    /**
     * Retrieves size from imp.ext.sharethrough.iframeSize or from im.banner.format
     */
    static Size getSize(Imp imp, ExtImpSharethrough extImpSharethrough) {
        final List<Integer> iframeSize = extImpSharethrough.getIframeSize();
        if (CollectionUtils.isNotEmpty(iframeSize) && iframeSize.size() >= 2 && !iframeSize.contains(0)) {
            return Size.of(iframeSize.get(0), iframeSize.get(1));
        } else {
            return getBiggestSizeFromBannerFormat(imp);
        }
    }

    /**
     * Retrieves banner from imp.banner and get the biggest format.
     */
    private static Size getBiggestSizeFromBannerFormat(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner == null) {
            return Size.of(1, 1);
        }

        return banner.getFormat().stream()
                .max(Comparator.comparingInt(format -> format.getW() * format.getH()))
                .map(format -> Size.of(format.getH(), format.getW()))
                .orElse(Size.of(1, 1));
    }

    /**
     * Retrieves gdpr from regs.ext.gdpr and in case of 1 returns true.
     */
    static boolean isConsentRequired(Regs regs) {
        final ObjectNode extRegsNode = regs != null ? regs.getExt() : null;
        final ExtRegs extRegs;
        try {
            extRegs = extRegsNode != null ? Json.mapper.treeToValue(extRegsNode, ExtRegs.class) : null;
        } catch (JsonProcessingException e) {
            return false;
        }

        return extRegs != null && extRegs.getGdpr() != null && extRegs.getGdpr() == 1;
    }

    /**
     * Retrieves page from site.page or null when site is null.
     */
    static String getPage(Site site) {
        return site != null ? site.getPage() : null;
    }

    /**
     * Retrieves consent from user.ext.consent and in case of any exception or invalid values return empty string.
     */
    static String getConsent(ExtUser extUser) {
        final String gdprConsent = extUser != null ? extUser.getConsent() : "";
        return ObjectUtils.firstNonNull(gdprConsent, "");
    }

    /**
     * Retrieves {@link ExtUser} from user.ext or returns null in case of exception or when user or user.ext null
     */
    static ExtUser getExtUser(User user) {
        final ObjectNode extUserNode = user != null ? user.getExt() : null;
        try {
            return extUserNode != null ? Json.mapper.treeToValue(extUserNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Retrieves device.ua from device or null if device is null
     */
    static String getUa(Device device) {
        return device != null ? device.getUa() : null;
    }

    /**
     * Check min browser version from userAgent
     */
    static boolean canBrowserAutoPlayVideo(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return false;
        }

        if (HttpUserAgentUtil.isAndroid(userAgent)) {
            return HttpUserAgentUtil.isAtMinChromeVersion(userAgent, MIN_CHROME_VERSION);
        } else if (HttpUserAgentUtil.isIos(userAgent)) {
            return HttpUserAgentUtil.isAtMinSafariVersion(userAgent, MIN_SAFARI_VERSION)
                    || HttpUserAgentUtil.isAtMinChromeIosVersion(userAgent, MIN_CHROME_VERSION);
        }
        return true;
    }

    /**
     * Return uri host or empty string in case of bad uri
     */
    static String getHost(String uriString) {
        if (StringUtils.isBlank(uriString)) {
            return "";
        }
        try {
            final URI uri = new URI(uriString);
            final String host = uri.getHost();
            return host != null ? host : "";
        } catch (URISyntaxException e) {
            return "";
        }
    }
}

