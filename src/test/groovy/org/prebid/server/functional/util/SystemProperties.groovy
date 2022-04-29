package org.prebid.server.functional.util

class SystemProperties {

    public static final String PBS_VERSION = System.getProperty("pbs.version")?.split("-")?.first()
    public static final String MOCKSERVER_VERSION = System.getProperty("mockserver.version")
    public static final int DEFAULT_TIMEOUT = getPropertyOrDefault("tests.default-timeout-millis", 5000)

    static <T> T getPropertyOrDefault(String property, T defaultValue) {
        (System.getProperty(property) ?: defaultValue) as T
    }

    private SystemProperties() {
        throw new InstantiationException("This class should not be instantiated!")
    }
}
