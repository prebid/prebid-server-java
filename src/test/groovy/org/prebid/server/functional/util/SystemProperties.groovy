package org.prebid.server.functional.util

class SystemProperties {

    public static final PBS_HOST = System.getProperty("pbs.host")
    public static final PBS_PORT = System.getProperty("pbs.port")
    public static final PBS_ADMIN_PORT = System.getProperty("pbs.admin.port")
    public static final BIDDER_HOST = System.getProperty("bidder.host")
    public static final BIDDER_PORT = System.getProperty("bidder.port")

    private SystemProperties() {
        throw new InstantiationException("This class should not be instantiated!")
    }
}
