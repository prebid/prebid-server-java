package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.UserAgent;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.UserAgentEvidenceConverter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers.MergingConfiguratorImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers.PropertyMergeImp;

import java.util.List;
import java.util.Map;

public final class UserAgentEvidenceConverterImp implements UserAgentEvidenceConverter {
    private static final MergingConfiguratorImp<Map<String, String>, BrandVersion> PLATFORM_MERGER = new MergingConfiguratorImp<>(
            List.of(
                    new PropertyMergeImp<>(BrandVersion::getBrand, b -> !b.isEmpty(), (evidence, platformName) ->
                            evidence.put("header.Sec-CH-UA-Platform", '"' + toHeaderSafe(platformName) + '"')
                    ),
                    new PropertyMergeImp<>(BrandVersion::getVersion, v -> !v.isEmpty(), (evidence, platformVersions) -> {
                        final StringBuilder s = new StringBuilder();
                        s.append('"');
                        appendVersionList(s, platformVersions);
                        s.append('"');
                        evidence.put("header.Sec-CH-UA-Platform-Version", s.toString());
                    })));

    private static final MergingConfiguratorImp<Map<String, String>, UserAgent> AGENT_MERGER = new MergingConfiguratorImp<>(
            List.of(
                    new PropertyMergeImp<>(UserAgent::getBrowsers, b -> !b.isEmpty(), (evidence, versions) -> {
                        final String fullUA = brandListToString(versions);
                        evidence.put("header.Sec-CH-UA", fullUA);
                        evidence.put("header.Sec-CH-UA-Full-Version-List", fullUA);
                    }),
                    new PropertyMergeImp<>(UserAgent::getPlatform, b -> true, PLATFORM_MERGER::applyProperties),
                    new PropertyMergeImp<>(UserAgent::getMobile, b -> true, (evidence, isMobile) ->
                            evidence.put("header.Sec-CH-UA-Mobile", "?" + isMobile)),
                    new PropertyMergeImp<>(UserAgent::getArchitecture, s -> !s.isEmpty(), (evidence, architecture) ->
                            evidence.put("header.Sec-CH-UA-Arch", '"' + toHeaderSafe(architecture) + '"')),
                    new PropertyMergeImp<>(UserAgent::getBitness, s -> !s.isEmpty(), (evidence, bitness) ->
                            evidence.put("header.Sec-CH-UA-Bitness", '"' + toHeaderSafe(bitness) + '"')),
                    new PropertyMergeImp<>(UserAgent::getModel, s -> !s.isEmpty(), (evidence, model) ->
                            evidence.put("header.Sec-CH-UA-Model", '"' + toHeaderSafe(model) + '"'))));

    @Override
    public void accept(UserAgent userAgent, Map<String, String> evidence) {
        if (userAgent != null) {
            AGENT_MERGER.applyProperties(evidence, userAgent);
        }
    }

    private static String brandListToString(List<BrandVersion> versions) {
        final StringBuilder s = new StringBuilder();
        for (BrandVersion nextBrandVersion : versions) {
            final String brandName = nextBrandVersion.getBrand();
            if (brandName == null) {
                continue;
            }
            if (!s.isEmpty()) {
                s.append(", ");
            }
            s.append('"');
            s.append(toHeaderSafe(brandName));
            s.append("\";v=\"");
            appendVersionList(s, nextBrandVersion.getVersion());
            s.append('"');
        }
        return s.toString();
    }

    private static void appendVersionList(StringBuilder s, List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return;
        }
        boolean isFirstVersionFragment = true;
        for (String nextFragment : versions) {
            if (!isFirstVersionFragment) {
                s.append('.');
            }
            s.append(nextFragment);
            isFirstVersionFragment = false;
        }
    }

    private static String toHeaderSafe(String rawValue) {
        return rawValue.replace("\"", "\\\"");
    }
}
