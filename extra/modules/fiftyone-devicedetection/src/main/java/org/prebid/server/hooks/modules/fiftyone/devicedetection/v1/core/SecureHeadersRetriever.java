package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.UserAgent;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Nonnull;

public class SecureHeadersRetriever {

    private SecureHeadersRetriever() {
    }

    public static Map<String, String> retrieveFrom(@Nonnull UserAgent userAgent) {

        final Map<String, String> secureHeaders = new HashMap<>();

        final List<BrandVersion> versions = userAgent.getBrowsers();
        if (CollectionUtils.isNotEmpty(versions)) {
            final String fullUA = brandListToString(versions);
            secureHeaders.put("header.Sec-CH-UA", fullUA);
            secureHeaders.put("header.Sec-CH-UA-Full-Version-List", fullUA);
        }

        final BrandVersion platform = userAgent.getPlatform();
        if (platform != null) {
            final String platformName = platform.getBrand();
            if (StringUtils.isNotBlank(platformName)) {
                secureHeaders.put("header.Sec-CH-UA-Platform", toHeaderSafe(platformName));
            }

            final List<String> platformVersions = platform.getVersion();
            if (CollectionUtils.isNotEmpty(platformVersions)) {
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append('"');
                appendVersionList(stringBuilder, platformVersions);
                stringBuilder.append('"');
                secureHeaders.put("header.Sec-CH-UA-Platform-Version", stringBuilder.toString());
            }
        }

        final Integer isMobile = userAgent.getMobile();
        if (isMobile != null) {
            secureHeaders.put("header.Sec-CH-UA-Mobile", "?" + isMobile);
        }

        final String architecture = userAgent.getArchitecture();
        if (StringUtils.isNotBlank(architecture)) {
            secureHeaders.put("header.Sec-CH-UA-Arch", toHeaderSafe(architecture));
        }

        final String bitness = userAgent.getBitness();
        if (StringUtils.isNotBlank(bitness)) {
            secureHeaders.put("header.Sec-CH-UA-Bitness", toHeaderSafe(bitness));
        }

        final String model = userAgent.getModel();
        if (StringUtils.isNotBlank(model)) {
            secureHeaders.put("header.Sec-CH-UA-Model", toHeaderSafe(model));
        }

        return secureHeaders;
    }

    private static String toHeaderSafe(String rawValue) {

        return '"' + rawValue.replace("\"", "\\\"") + '"';
    }

    private static String brandListToString(List<BrandVersion> versions) {

        final StringBuilder stringBuilder = new StringBuilder();
        for (BrandVersion nextBrandVersion : versions) {
            final String brandName = nextBrandVersion.getBrand();
            if (brandName == null) {
                continue;
            }
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(toHeaderSafe(brandName));
            stringBuilder.append(";v=\"");
            appendVersionList(stringBuilder, nextBrandVersion.getVersion());
            stringBuilder.append('"');
        }
        return stringBuilder.toString();
    }

    private static void appendVersionList(StringBuilder stringBuilder, List<String> versions) {

        if (CollectionUtils.isEmpty(versions)) {
            return;
        }
        boolean isFirstVersionFragment = true;
        for (String nextFragment : versions) {
            if (!isFirstVersionFragment) {
                stringBuilder.append('.');
            }
            stringBuilder.append(nextFragment);
            isFirstVersionFragment = false;
        }
    }
}
