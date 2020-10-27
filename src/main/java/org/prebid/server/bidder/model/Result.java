package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * Defines generic result that might bear error alongside.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class Result<T> {

    T value;

    List<BidderError> errors;

    public static <R> Result<List<R>> emptyWithError(BidderError error) {
        return Result.of(Collections.emptyList(), Collections.singletonList(error));
    }

    public static <R> Result<List<R>> empty() {
        return Result.of(Collections.emptyList(), Collections.emptyList());
    }
}
