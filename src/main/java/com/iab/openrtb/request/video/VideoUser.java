package com.iab.openrtb.request.video;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class VideoUser {

    Map<String, String> buyeruids;

    /**
     * Year of birth as a 4-digit integer.
     */
    Integer yob;

    /**
     * Comma separated list of keywords, interests, or intent.
     */
    String keywords;

    /**
     * Gender, where “M” = male, “F” = female, “O” = known to be other (i.e.,
     * omitted is unknown).
     */
    String gender;

    Gdpr gdpr;
}

