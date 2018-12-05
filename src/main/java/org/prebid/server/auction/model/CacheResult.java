package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.cache.model.CacheIdInfo;

import java.util.List;
import java.util.Map;

@Value
@AllArgsConstructor(staticName = "of")
public class CacheResult {

    Map<Bid, CacheIdInfo> cacheBids;

    List<String> errors;

    int executionTime;
}
