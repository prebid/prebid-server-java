package org.prebid.server.bidder.yieldlab.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value(staticConstructor = "of")
public class YieldlabDigitalServicesActResponse {

    String behalf;

    String paid;

    Integer adrender;

    List<Transparency> transparency;

    @AllArgsConstructor(staticName = "of")
    @Value(staticConstructor = "of")
    public static class Transparency {
        String domain;
        List<Integer> dsaparams;
    }
}
