package org.prebid.server.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtil {

    private StreamUtil() {
    }

    public static <T> Stream<T> asStream(Spliterator<T> spliterator) {
        return StreamSupport.stream(spliterator, false);
    }

    public static <T> Stream<T> asStream(Iterator<T> iterator) {
        return StreamSupport.stream(IterableUtil.iterable(iterator).spliterator(), false);
    }

    public static <T> Predicate<T> distinctBy(Function<? super T, ?> keyExtractor) {
        final Set<Object> seen = new HashSet<>();
        return value -> seen.add(keyExtractor.apply(value));
    }
}
