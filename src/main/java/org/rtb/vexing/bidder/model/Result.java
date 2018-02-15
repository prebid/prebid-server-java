package org.rtb.vexing.bidder.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.List;

/**
 * Defines generic result that might bear error alongside.
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class Result<T> {

    T value;

    List<String> errors;

    public static Result<List<HttpRequest>> emptyHttpRequests() {
        return Result.of(Collections.<HttpRequest>emptyList(), Collections.emptyList());
    }
}
