package org.prebid.server.util.system;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.prebid.server.vertx.Initializable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.Objects;

public class CpuLoadAverageStats implements Initializable {

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
    private final CentralProcessor cpu = hardwareAbstractionLayer.getProcessor();

    private final Vertx vertx;

    private final long measurementIntervalMillis;

    private volatile double cpuLoadAverage = -1;
    private volatile long[] prevTicks = new long[CentralProcessor.TickType.values().length];

    public CpuLoadAverageStats(Vertx vertx, long measurementIntervalMillis) {
        this.vertx = Objects.requireNonNull(vertx);
        this.measurementIntervalMillis = measurementIntervalMillis;
        if (measurementIntervalMillis <= 1000) {
            throw new IllegalArgumentException("Measurement interval should be greater than 1 second");
        }
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        measureCpuLoad();
        vertx.setPeriodic(measurementIntervalMillis, timerId -> measureCpuLoad());
        initializePromise.tryComplete();
    }

    private void measureCpuLoad() {
        cpuLoadAverage = cpu.getSystemCpuLoadBetweenTicks(prevTicks);
        prevTicks = cpu.getSystemCpuLoadTicks();
    }

    public double getCpuLoadAverage() {
        return cpuLoadAverage;
    }
}
