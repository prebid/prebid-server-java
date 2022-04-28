package org.prebid.server.functional.util

class SystemProperties {

    public static final PBS_VERSION = System.getProperty("pbs.version")?.split("-")?.first()
    public static final MOCKSERVER_VERSION = System.getProperty("mockserver.version")

    static String getPropertyOrDefault(String property, String defaultValue) {
        System.getProperty(property) ?: defaultValue
    }

    static String getPropertyOrDefault(String property, boolean defaultValue) {
        System.getProperty(property) ?: defaultValue
    }

    static int getIntPropertyOrDefault(String property, int defaultValue) {
        System.getProperty(property) ? Integer.parseInt(System.getProperty(property)) : defaultValue
    }

    private SystemProperties() {
        throw new InstantiationException("This class should not be instantiated!")
    }
}
