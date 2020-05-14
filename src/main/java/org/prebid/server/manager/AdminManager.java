package org.prebid.server.manager;

import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * class to manage requests from Admin panel
 */
public class AdminManager {

    public static final String COUNTER_KEY = "admin_counter";
    public static final String TIME_KEY = "admin_time";

    private ConcurrentHashMap<String, Rule<?, ?>> actionMap;

    public AdminManager() {
        actionMap = new ConcurrentHashMap<>();
    }

    /**
     * @param key      key of the given action
     * @param amount   max count of action executions
     * @param action   do while counter is less then max value
     * @param onFinish do when counter is over then max value
     */
    public void setupByCounter(String key, Integer amount, BiConsumer<?, ?> action, BiConsumer<?, ?> onFinish) {
        actionMap.put(key, new CounterRule(action, onFinish, amount));
    }

    /**
     * @param key        key of the given action
     * @param timeMillis duration in millis
     * @param action     do while time is not over
     * @param onFinish   do when time is over
     */
    public void setupByTime(String key, long timeMillis, BiConsumer<?, ?> action, BiConsumer<?, ?> onFinish) {
        actionMap.put(key, new TimeRule(action, onFinish, timeMillis));
    }

    /**
     * Runs the BiConsumer by key.
     */
    @SuppressWarnings("unchecked")
    public <T, U> void accept(String key, T t, U u) {
        if (contains(key)) {
            final Rule<T, U> rule = (Rule<T, U>) actionMap.get(key);
            rule.applyRule().accept(t, u);
        }
    }

    /**
     * Returns true if Rule is contains by key.
     */
    public boolean contains(String key) {
        return actionMap.containsKey(key);
    }

    /**
     * Returns state of BiConsumer by key.
     */
    public boolean isRunning(String key) {
        return contains(key) && !actionMap.get(key).isFinished;
    }

    @AllArgsConstructor
    private abstract static class Rule<T, U> {

        protected BiConsumer<T, U> onRun;

        protected BiConsumer<T, U> onFinish;

        protected boolean isFinished;

        abstract BiConsumer<T, U> applyRule();
    }

    private static class TimeRule<T, U> extends Rule<T, U> {

        private Instant time;

        TimeRule(BiConsumer<T, U> onRun, BiConsumer<T, U> onFinish, Long timeMillis) {
            super(onRun, onFinish, false);
            this.onFinish = onFinish;
            this.time = Instant.now().plusMillis(timeMillis);
        }

        @Override
        BiConsumer<T, U> applyRule() {
            if (time != null && time.isAfter(Instant.now())) {
                return onRun;
            }
            this.isFinished = true;
            return onFinish;
        }
    }

    private static class CounterRule<T, U> extends Rule<T, U> {

        private AtomicInteger counter;

        CounterRule(BiConsumer<T, U> onRun, BiConsumer<T, U> onFinish, Integer counter) {
            super(onRun, onFinish, false);
            this.counter = new AtomicInteger(counter);
        }

        @Override
        BiConsumer<T, U> applyRule() {
            if (counter != null && counter.get() > 0) {
                counter.decrementAndGet();
                return onRun;
            }
            this.isFinished = true;
            return onFinish;
        }
    }
}
