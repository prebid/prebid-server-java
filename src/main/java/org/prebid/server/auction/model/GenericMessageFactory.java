package org.prebid.server.auction.model;

import java.util.Collections;
import java.util.Set;

public class GenericMessageFactory {

    private static final Set<String> tags = Collections.singleton("GENERIC");

    public static void addGeneric(Set<String> set) {
        set.addAll(tags);
    }

}
