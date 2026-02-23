package org.prebid.server.analytics.reporter.agma;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventBuffer<T> {

    private final Lock lock = new ReentrantLock(true);

    private List<T> events = new ArrayList<>();

    private long byteSize;

    private final long maxEvents;

    private final long maxBytes;

    public EventBuffer(long maxEvents, long maxBytes) {
        this.maxEvents = maxEvents;
        this.maxBytes = maxBytes;
    }

    public void put(T event, long eventSize) {
        lock.lock();
        events.addLast(event);
        byteSize += eventSize;
        lock.unlock();
    }

    public List<T> pollToFlush() {
        List<T> toFlush = Collections.emptyList();

        lock.lock();
        if (events.size() >= maxEvents || byteSize >= maxBytes) {
            toFlush = events;
            reset();
        }
        lock.unlock();

        return toFlush;
    }

    public List<T> pollAll() {
        lock.lock();
        final List<T> polled = events;
        reset();
        lock.unlock();

        return polled;
    }

    private void reset() {
        byteSize = 0;
        events = new ArrayList<>();
    }
}
