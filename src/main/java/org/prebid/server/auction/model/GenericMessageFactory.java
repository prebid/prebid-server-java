package org.prebid.server.auction.model;

import java.util.Collections;
import java.util.Set;

public class GenericMessageFactory {

    private static final Set<String> GENERIC_TAG = Collections.singleton("GENERIC");

    private GenericMessageFactory() {
    }

    public static void addGeneric(Set<String> set) {
        set.addAll(GENERIC_TAG);
    }

}
