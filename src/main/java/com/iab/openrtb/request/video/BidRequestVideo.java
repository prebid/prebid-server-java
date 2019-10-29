package com.iab.openrtb.request.video;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.PriceGranularity;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class BidRequestVideo {

    // 	Optional (Required for Xandr PBS)
    String storedrequestid;

    Podconfig podconfig;

    Site site;

    App app;

    com.iab.openrtb.request.Video video;

    IncludeBrandCategory includebrandcategory;

    Content content;

    Cacheconfig cacheconfig;

    Integer test;

    Long tmax;

    List<String> bcat;

    List<String> badv;

    // Add GDPR
    User user;

    Device device;

    PriceGranularity priceGranularity;
}
