package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ServerConfiguration {

    String adCurrency;

    List<String> blacklistedApps;

    String externalUrl;

    Integer gdprHostVendorId;

    String datacenterRegion;
}
