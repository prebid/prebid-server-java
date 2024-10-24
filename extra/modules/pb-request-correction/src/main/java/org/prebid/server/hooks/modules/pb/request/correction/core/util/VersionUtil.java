package org.prebid.server.hooks.modules.pb.request.correction.core.util;

public class VersionUtil {

    public static boolean isVersionLessThan(String versionAsString, int major, int minor, int patch) {
        return compareVersion(versionAsString, major, minor, patch) < 0;
    }

    private static int compareVersion(String versionAsString, int major, int minor, int patch) {
        final String[] version = versionAsString.split("\\.");

        final int parsedMajor = getAtAsIntOrDefault(version, 0, -1);
        final int parsedMinor = getAtAsIntOrDefault(version, 1, 0);
        final int parsedPatch = getAtAsIntOrDefault(version, 2, 0);

        int diff = parsedMajor >= 0 ? parsedMajor - major : 1;
        diff = diff == 0 ? parsedMinor - minor : diff;
        diff = diff == 0 ? parsedPatch - patch : diff;

        return diff;
    }

    private static int getAtAsIntOrDefault(String[] array, int index, int defaultValue) {
        return array.length > index ? intOrDefault(array[index], defaultValue) : defaultValue;
    }

    private static int intOrDefault(String intAsString, int defaultValue) {
        try {
            final int parsed = Integer.parseInt(intAsString);
            return parsed >= 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
