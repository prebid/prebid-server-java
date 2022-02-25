package org.prebid.server.auction.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ServerConfigurationProperties {

    boolean cacheOnlyWinningBids;

    String adCurrency;

    List<String> blacklistedApps;

    String externalUrl;

    Integer gdprHostVendorId;

    String datacenterRegion;
}
