package org.prebid.server.version;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class PrebidVersionProvider {

    private final String pbsRecord;

    public PrebidVersionProvider(String pbsVersion) {
        pbsRecord = createNameVersionRecord("pbs-java", Objects.requireNonNull(pbsVersion));
    }

    public String getNameVersionRecord() {
        return pbsRecord;
    }

    private static String createNameVersionRecord(String name, String version) {
        return StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(version)
                ? String.format("%s/%s", name, version)
                : null;
    }
}
