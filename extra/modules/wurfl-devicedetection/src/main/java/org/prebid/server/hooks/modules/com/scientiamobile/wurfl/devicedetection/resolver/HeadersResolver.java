package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.resolver;

import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class HeadersResolver {

    static final String SEC_CH_UA = "Sec-CH-UA";
    static final String SEC_CH_UA_PLATFORM = "Sec-CH-UA-Platform";
    static final String SEC_CH_UA_PLATFORM_VERSION = "Sec-CH-UA-Platform-Version";
    static final String SEC_CH_UA_MOBILE = "Sec-CH-UA-Mobile";
    static final String SEC_CH_UA_ARCH = "Sec-CH-UA-Arch";
    static final String SEC_CH_UA_MODEL = "Sec-CH-UA-Model";
    static final String SEC_CH_UA_FULL_VERSION_LIST = "Sec-CH-UA-Full-Version-List";
    static final String USER_AGENT = "User-Agent";

    public Map<String, String> resolve(final Device device, final Map<String, String> headers) {

        if (Objects.isNull(device) && Objects.isNull(headers)) {
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

        if (Objects.isNull(device)) {
            log.warn("ORBT Device is null");
            return resolvedHeaders;
        }

        if (Objects.nonNull(device.getUa())) {
            resolvedHeaders.put(USER_AGENT, device.getUa());
        }

        resolvedHeaders.putAll(resolveFromSua(device.getSua()));
        return resolvedHeaders;
    }

    private Map<String, String> resolveFromSua(UserAgent sua) {

        final Map<String, String> headers = new HashMap<>();

        if (Objects.isNull(sua)) {
            log.warn("Sua is null, returning empty headers");
            return new HashMap<>();
        }

        // Browser brands and versions
        final List<BrandVersion> brands = sua.getBrowsers();
        if (CollectionUtils.isEmpty(brands)) {
            log.warn("No browser brands and versions found");
            return headers;
        }

        final String brandList = brandListAsString(brands);
        headers.put(SEC_CH_UA, brandList);
        headers.put(SEC_CH_UA_FULL_VERSION_LIST, brandList);

        // Platform
        final PlatformNameVersion platformNameVersion = PlatformNameVersion.from(sua.getPlatform());
        if (Objects.nonNull(platformNameVersion)) {
            headers.put(SEC_CH_UA_PLATFORM, escape(platformNameVersion.getPlatformName()));
            headers.put(SEC_CH_UA_PLATFORM_VERSION, escape(platformNameVersion.getPlatformVersion()));
        }

        // Model
        final String model = sua.getModel();
        if (Objects.nonNull(model) && !model.isEmpty()) {
            headers.put(SEC_CH_UA_MODEL, escape(model));
        }

        // Architecture
        final String arch = sua.getArchitecture();
        if (Objects.nonNull(arch) && !arch.isEmpty()) {
            headers.put(SEC_CH_UA_ARCH, escape(arch));
        }

        // Mobile
        final Integer mobile = sua.getMobile();
        if (Objects.nonNull(mobile)) {
            headers.put(SEC_CH_UA_MOBILE, "?" + mobile);
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
