package org.prebid.server.functional.util

class SystemProperties {

    public static final String PBS_VERSION = System.getProperty("pbs.version")?.split("-")?.first()
    public static final String MOCKSERVER_VERSION = System.getProperty("mockserver.version")
    public static final boolean USE_FIXED_CONTAINER_PORTS = getPropertyOrDefault("tests.fixed-container-ports", false)
    public static final int DEFAULT_TIMEOUT = 5000

    static int getPropertyOrDefault(String property, int defaultValue) {
        System.getProperty(property) as Integer ?: defaultValue
    }

    static String getPropertyOrDefault(String property, String defaultValue) {
        System.getProperty(property) ?: defaultValue
    }

    static boolean getPropertyOrDefault(String property, boolean defaultValue) {
        Boolean.parseBoolean(System.getProperty(property)) ?: defaultValue
    }

    private SystemProperties() {
        throw new InstantiationException("This class should not be instantiated!")
    }
}
