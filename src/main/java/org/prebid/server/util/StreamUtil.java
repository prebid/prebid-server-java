package org.prebid.server.util;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtil {

    private StreamUtil() {
    }

    public static <T> Stream<T> asStream(Spliterator<T> spliterator) {
        return StreamSupport.stream(spliterator, false);
    }

    public static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
