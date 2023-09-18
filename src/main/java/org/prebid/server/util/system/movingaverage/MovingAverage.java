package org.prebid.server.util.system.movingaverage;

import java.util.concurrent.atomic.AtomicReference;

public class MovingAverage {

    private final AtomicReference<ImmutableWindowSum> windowSum;

    public MovingAverage(int windowSize) {
        windowSum = new AtomicReference<>(new ImmutableWindowSum(windowSize));
    }

    public void record(double measurement) {
        ImmutableWindowSum previousSum;
        ImmutableWindowSum updatedSum;

        do {
            previousSum = windowSum.get();
            updatedSum = previousSum.record(measurement);
        } while (!windowSum.compareAndSet(previousSum, updatedSum));
    }

    public double getAverage() {
        final ImmutableWindowSum window = windowSum.get();
        final int windowSize = window.getWindowSize();
        return windowSize != 0 ? window.getSum() / windowSize : -1;
    }
}
