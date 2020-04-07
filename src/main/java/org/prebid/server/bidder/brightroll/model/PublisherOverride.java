package org.prebid.server.bidder.brightroll.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(staticName = "of")
public class PublisherOverride {
    /**
     * Blocked advertisers.
     */
    private List<String> badv;

    /**
     * Blocked advertisers.
     */
    private List<String> bcat;

    /**
     * Blocked IAB categories.
     */
    private List<Integer> impBattr;
}
