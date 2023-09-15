package org.prebid.server.auction;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

@Slf4j
public class AuctionThrottler {

    private final OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    private final int cpus = operatingSystemMXBean.getAvailableProcessors();
    private final double softThreshold;
    private final double hardThreshold;

    public AuctionThrottler(double softThreshold, double hardThreshold) {
        this.softThreshold = softThreshold;
        this.hardThreshold = hardThreshold;

        validateThrottlingArguments(softThreshold, hardThreshold);
    }

    private void validateThrottlingArguments(double softThreshold, double hardThreshold) {
        if (softThreshold >= hardThreshold || softThreshold < 0 || hardThreshold > 1) {
            throw new IllegalArgumentException("Arguments should be 0 <= soft threshold < hard threshold <= 1");
        }
    }

    public static final class ThrottleNotNeeded extends ThrottlingResult {
        private ThrottleNotNeeded() {
        }

        public static final ThrottleNotNeeded INSTANCE = new ThrottleNotNeeded();
    }

    @Value(staticConstructor = "of")
    public static final class ThrottleRequired extends ThrottlingResult {

        double amount;
    }

    public static sealed class ThrottlingResult permits ThrottleNotNeeded, ThrottleRequired {
    }

    public ThrottlingResult getThrottlingStatus() {
        final double throttlingPercentage = Math.min(1.0,
                (getCpuLoadAveragePercentage() - softThreshold) / (hardThreshold - softThreshold));

        return throttlingPercentage > 0 ? ThrottleRequired.of(throttlingPercentage) : ThrottleNotNeeded.INSTANCE;
    }

    public double getCpuLoadAveragePercentage() {
        return operatingSystemMXBean.getSystemLoadAverage() / cpus;
    }
}
