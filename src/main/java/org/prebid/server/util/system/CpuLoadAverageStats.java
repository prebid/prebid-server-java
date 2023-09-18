package org.prebid.server.util.system;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.prebid.server.util.system.movingaverage.MovingAverage;
import org.prebid.server.vertx.Initializable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.Objects;

@Slf4j
public class CpuLoadAverageStats implements Initializable {

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
    private final CentralProcessor cpu = hardwareAbstractionLayer.getProcessor();

    private final Vertx vertx;

    private final long measurementIntervalMillis;
    private final MovingAverage cpuLoadMovingAverage;

    private volatile long[] prevTicks = new long[CentralProcessor.TickType.values().length];

    public CpuLoadAverageStats(Vertx vertx, long measurementIntervalMillis, int maxWindowSize) {
        this.vertx = Objects.requireNonNull(vertx);
        this.measurementIntervalMillis = measurementIntervalMillis;
        if (measurementIntervalMillis <= 1000) {
            throw new IllegalArgumentException("Measurement interval should be greater than 1 second");
        }

        this.cpuLoadMovingAverage = new MovingAverage(maxWindowSize);
    }

    @Override
    public void initialize() {
        vertx.setPeriodic(measurementIntervalMillis, timerId -> measureCpuLoad());
    }

    private void measureCpuLoad() {
        cpuLoadMovingAverage.record(cpu.getSystemCpuLoadBetweenTicks(prevTicks));
        prevTicks = cpu.getSystemCpuLoadTicks();

        log.info("CPU load average is: %s".formatted(cpuLoadMovingAverage.getAverage()));
    }

    public double getCpuLoadAverage() {
        return cpuLoadMovingAverage.getAverage();
    }
}
