package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class UserFpd {

    public static final UserFpd EMPTY = UserFpd.builder().build();

    /**
     * Year of birth as a 4-digit integer.
     */
    Integer yob;

    /**
     * Gender, where “M” = male, “F” = female, “O” = known to be other (i.e.,
     * omitted is unknown).
     */
    String gender;

    /**
     * Comma separated list of keywords, interests, or intent.
     */
    String keywords;
}
