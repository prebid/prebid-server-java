package org.prebid.server.util.system.movingaverage;

import java.util.ArrayDeque;
import java.util.Queue;

public class ImmutableWindowSum {

    private final Queue<Double> window;

    private final int maxWindowSize;
    private final double sum;

    private ImmutableWindowSum(Queue<Double> window, int maxWindowSize, double sum) {
        this.window = window;
        this.maxWindowSize = maxWindowSize;
        this.sum = sum;
    }

    public ImmutableWindowSum(int maxWindowSize) {
        if (maxWindowSize <= 0) {
            throw new IllegalArgumentException("Window size should be greater than 0");
        }

        this.maxWindowSize = maxWindowSize;
        this.sum = 0;
        this.window = new ArrayDeque<>(maxWindowSize);
    }

    public ImmutableWindowSum record(double measurement) {
        double newSum = sum + measurement;
        final Queue<Double> windowCopy = new ArrayDeque<>(window);
        windowCopy.add(measurement);

        if (windowCopy.size() > maxWindowSize) {
            newSum -= windowCopy.remove();
        }

        return new ImmutableWindowSum(windowCopy, maxWindowSize, newSum);
    }

    public double getSum() {
        return sum;
    }

    public int getWindowSize() {
        return window.size();
    }
}
