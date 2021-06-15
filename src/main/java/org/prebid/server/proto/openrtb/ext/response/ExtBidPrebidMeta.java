package org.prebid.server.proto.openrtb.ext.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtBidPrebidMeta {

    Integer advertiserId;

    String advertiserName;

    Integer agencyId;

    String agencyName;

    Integer brandId;

    String brandName;

    String mediaType;

    Integer networkId;

    String networkName;

    String primaryCatId;

    List<String> secondaryCatIds;
}
