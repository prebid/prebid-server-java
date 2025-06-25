package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.resolver;

import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.prebid.server.util.HttpUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HeadersResolver {

    public Map<String, String> resolve(Device device, Map<String, String> headers) {
        if (device == null && headers == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> resolvedHeaders = resolveFromDevice(device);
        return MapUtils.isNotEmpty(resolvedHeaders)
                ? resolvedHeaders
                : headers;
    }

    private Map<String, String> resolveFromDevice(Device device) {
        if (device == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> resolvedHeaders = new HashMap<>();
        if (device.getUa() != null) {
            resolvedHeaders.put(HttpUtil.USER_AGENT_HEADER.toString(), device.getUa());
        }
        resolvedHeaders.putAll(resolveFromSua(device.getSua()));

        return resolvedHeaders;
    }

    private Map<String, String> resolveFromSua(UserAgent sua) {
        if (sua == null) {
            return Collections.emptyMap();
        }

        final List<BrandVersion> brands = sua.getBrowsers();
        if (CollectionUtils.isEmpty(brands)) {
            return Collections.emptyMap();
        }

        final Map<String, String> headers = new HashMap<>();
        final String brandList = brandListAsString(brands);
        headers.put(HttpUtil.SEC_CH_UA.toString(), brandList);
        headers.put(HttpUtil.SEC_CH_UA_FULL_VERSION_LIST.toString(), brandList);

        final BrandVersion platform = sua.getPlatform();
        if (platform != null) {
            headers.put(HttpUtil.SEC_CH_UA_PLATFORM.toString(), platform.getBrand());
            headers.put(HttpUtil.SEC_CH_UA_PLATFORM_VERSION.toString(),
                    versionFromTokens(platform.getVersion()));
        }

        final String model = sua.getModel();
        if (StringUtils.isNotEmpty(model)) {
            headers.put(HttpUtil.SEC_CH_UA_MODEL.toString(), model);
        }

        final String arch = sua.getArchitecture();
        if (StringUtils.isNotEmpty(arch)) {
            headers.put(HttpUtil.SEC_CH_UA_ARCH.toString(), arch);
        }

        final Integer mobile = sua.getMobile();
        if (mobile != null) {
            headers.put(HttpUtil.SEC_CH_UA_MOBILE.toString(), "?" + mobile);
        }

        return headers;
    }

    private String brandListAsString(List<BrandVersion> versions) {
        return versions.stream()
                .filter(brandVersion -> brandVersion.getBrand() != null)
                .map(brandVersion -> "\"%s\";v=\"%s\"".formatted(
                        brandVersion.getBrand(),
                        versionFromTokens(brandVersion.getVersion())))
                .collect(Collectors.joining(", "));
    }

    private static String versionFromTokens(List<String> tokens) {
        if (CollectionUtils.isEmpty(tokens)) {
            return StringUtils.EMPTY;
        }

        return tokens.stream()
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.joining("."));
    }
}
