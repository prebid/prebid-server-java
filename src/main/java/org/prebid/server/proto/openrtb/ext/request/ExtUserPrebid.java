package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;
import java.util.TreeMap;

/**
 * Defines the the contract for bidrequest.user.ext.prebid
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtUserPrebid {

    Map<String, String> buyeruids = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    @JsonAnySetter
    private void setBuyeruids(String key, String value) {
        buyeruids.put(key, value);
    }
}
