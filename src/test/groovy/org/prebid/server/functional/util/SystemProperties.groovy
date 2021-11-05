package org.prebid.server.functional.util

class SystemProperties {

    public static final PBS_VERSION = System.getProperty("pbs.version")?.split("-")?.first()

    private SystemProperties() {
        throw new InstantiationException("This class should not be instantiated!")
    }
}
