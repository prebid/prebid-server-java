package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.resolver;

import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HeadersResolver {

    private static final Logger LOG = LoggerFactory.getLogger(HeadersResolver.class);

    public Map<String, String> resolve(Device device, Map<String, String> headers) {
        if (device == null && headers == null) {
            return new HashMap<>();
        }

        final Map<String, String> resolvedHeaders = resolveFromOrtbDevice(device);
        if (MapUtils.isEmpty(resolvedHeaders)) {
            return headers;
        }

        return resolvedHeaders;
    }

    private Map<String, String> resolveFromOrtbDevice(Device device) {
        final Map<String, String> resolvedHeaders = new HashMap<>();

        if (device == null) {
            LOG.warn("ORBT Device is null");
            return resolvedHeaders;
        }

        if (device.getUa() != null) {
            resolvedHeaders.put(HttpUtil.USER_AGENT_HEADER.toString(), device.getUa());
        }

        resolvedHeaders.putAll(resolveFromSua(device.getSua()));
        return resolvedHeaders;
    }

    private Map<String, String> resolveFromSua(UserAgent sua) {
        final Map<String, String> headers = new HashMap<>();

        if (sua == null) {
            LOG.warn("Sua is null, returning empty headers");
            return new HashMap<>();
        }

        // Browser brands and versions
        final List<BrandVersion> brands = sua.getBrowsers();
        if (CollectionUtils.isEmpty(brands)) {
            LOG.warn("No browser brands and versions found");
            return headers;
        }

        final String brandList = brandListAsString(brands);
        headers.put(HttpUtil.SEC_CH_UA.toString(), brandList);
        headers.put(HttpUtil.SEC_CH_UA_FULL_VERSION_LIST.toString(), brandList);

        // Platform
        final BrandVersion brandNameVersion = sua.getPlatform();
        if (brandNameVersion != null) {
            headers.put(HttpUtil.SEC_CH_UA_PLATFORM.toString(), escape(brandNameVersion.getBrand()));
            headers.put(HttpUtil.SEC_CH_UA_PLATFORM_VERSION.toString(),
                    escape(versionFromTokens(brandNameVersion.getVersion())));
        }

        // Model
        final String model = sua.getModel();
        if (model != null && !model.isEmpty()) {
            headers.put(HttpUtil.SEC_CH_UA_MODEL.toString(), escape(model));
        }

        // Architecture
        final String arch = sua.getArchitecture();
        if (arch != null && !arch.isEmpty()) {
            headers.put(HttpUtil.SEC_CH_UA_ARCH.toString(), escape(arch));
        }

        // Mobile
        final Integer mobile = sua.getMobile();
        if (mobile != null) {
            headers.put(HttpUtil.SEC_CH_UA_MOBILE.toString(), "?" + mobile);
        }
        return headers;
    }

    private String brandListAsString(List<BrandVersion> versions) {
        final String brandNameString = versions.stream()
                .filter(brandVersion -> brandVersion.getBrand() != null)
                .map(brandVersion -> {
                    final String brandName = escape(brandVersion.getBrand());
                    final String versionString = versionFromTokens(brandVersion.getVersion());
                    return brandName + ";v=\"" + versionString + "\"";
                })
                .collect(Collectors.joining(", "));
        return brandNameString;
    }

    private static String escape(String value) {
        return '"' + value.replace("\"", "\\\"") + '"';
    }

    public static String versionFromTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }

        return tokens.stream()
                .filter(token -> token != null && !token.isEmpty())
                .collect(Collectors.joining("."));
    }
}
