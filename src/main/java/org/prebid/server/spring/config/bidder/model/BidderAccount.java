package org.prebid.server.spring.config.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BidderAccount {

    private String id;

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
