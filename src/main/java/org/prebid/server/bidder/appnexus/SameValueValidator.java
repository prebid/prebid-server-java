package org.prebid.server.bidder.appnexus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@NoArgsConstructor(staticName = "create")
public class SameValueValidator<T> {

    @Setter
    private T sameValue;

    public boolean isNotInitialised() {
        return sameValue == null;
    }

    public boolean isInvalid(T value) {
        return !Objects.equals(sameValue, value);
    }
}
