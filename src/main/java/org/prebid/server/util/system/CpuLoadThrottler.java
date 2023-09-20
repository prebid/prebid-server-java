package org.prebid.server.util.system;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.Objects;

public class CpuLoadThrottler {

    private final double softThreshold;
    private final double hardThreshold;

    private final CpuLoadAverageStats cpuLoadStats;

    public CpuLoadThrottler(CpuLoadAverageStats cpuLoadStats, double softThreshold, double hardThreshold) {
        this.cpuLoadStats = Objects.requireNonNull(cpuLoadStats);
        this.softThreshold = softThreshold;
        this.hardThreshold = hardThreshold;

        validateThrottlingArguments(softThreshold, hardThreshold);
    }

    private void validateThrottlingArguments(double softThreshold, double hardThreshold) {
        if (softThreshold >= hardThreshold || softThreshold < 0 || hardThreshold > 1) {
            throw new IllegalArgumentException("Arguments should be 0 <= soft threshold < hard threshold <= 1");
        }
    }

    public static sealed class ThrottlingResult permits ThrottleNotNeeded, ThrottleRequired {
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ThrottleNotNeeded extends ThrottlingResult {
        public static final ThrottleNotNeeded INSTANCE = new ThrottleNotNeeded();
    }

    @Value(staticConstructor = "of")
    public static class ThrottleRequired extends ThrottlingResult {

        double amount;
    }

    public ThrottlingResult getThrottlingStatus() {
        final double throttlingPercentage =
                (cpuLoadStats.getCpuLoadAverage() - softThreshold) / (hardThreshold - softThreshold);

        return throttlingPercentage > 0
                ? ThrottleRequired.of(throttlingPercentage)
                : ThrottleNotNeeded.INSTANCE;
    }
}
