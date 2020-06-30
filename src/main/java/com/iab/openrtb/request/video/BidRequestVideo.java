package com.iab.openrtb.request.video;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.PriceGranularity;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class BidRequestVideo {

    String storedrequestid;

    Podconfig podconfig;

    Site site;

    App app;

    Video video;

    IncludeBrandCategory includebrandcategory;

    Content content;

    CacheConfig cacheconfig;

    Integer test;

    Long tmax;

    List<String> bcat;

    List<String> badv;

    Regs regs;

    User user;

    Device device;

    PriceGranularity priceGranularity;
}

