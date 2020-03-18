package org.prebid.server.manager;

import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class AdminManager {

    public static final String ADMIN_COUNTER_KEY = "admin_counter";
    public static final String ADMIN_TIME_KEY = "admin_time";

    private ConcurrentHashMap<String, Rule<?, ?>> actionMap;

    public AdminManager() {
        actionMap = new ConcurrentHashMap<>();
    }

    public void setupByCounter(String key, Integer amount, BiConsumer<?, ?> action, BiConsumer<?, ?> onFinish) {
        actionMap.put(key, new CounterRule(action, onFinish, amount));
    }

    public void setupByTime(String key, long timeMillis, BiConsumer<?, ?> action, BiConsumer<?, ?> onFinish) {
        actionMap.put(key, new TimeRule(action, onFinish, timeMillis));
    }

    @SuppressWarnings("unchecked")
    public <T, U> void accept(String key, T t, U u) {
        final Rule<T, U> rule = (Rule<T, U>) actionMap.get(key);
        rule.applyRule().accept(t, u);
    }

    public boolean contains(String key) {
        return actionMap.containsKey(key);
    }

    @AllArgsConstructor
    private abstract static class Rule<T, U> {

        protected BiConsumer<T, U> onRun;

        protected BiConsumer<T, U> onFinish;

        abstract BiConsumer<T, U> applyRule();
    }

    private static class TimeRule<T, U> extends Rule<T, U> {

        private Long time;

        TimeRule(BiConsumer<T, U> onRun, BiConsumer<T, U> onFinish, Long timeMillis) {
            super(onRun, onFinish);
            this.onFinish = onFinish;
            this.time = Instant.now().toEpochMilli() + timeMillis;
        }

        @Override
        BiConsumer<T, U> applyRule() {
            if (time != null && Instant.now().toEpochMilli() < time) {
                return onRun;
            }
            return onFinish;
        }
    }

    private static class CounterRule<T, U> extends Rule<T, U> {

        private AtomicInteger counter;

        CounterRule(BiConsumer<T, U> onRun, BiConsumer<T, U> onFinish, Integer counter) {
            super(onRun, onFinish);
            this.counter = new AtomicInteger(counter);
        }

        @Override
        BiConsumer<T, U> applyRule() {
            if (counter != null && counter.get() > 0) {
                counter.decrementAndGet();
                return onRun;
            }
            return onFinish;
        }
    }

}
