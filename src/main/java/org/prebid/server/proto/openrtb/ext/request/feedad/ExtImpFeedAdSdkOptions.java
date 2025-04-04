package org.prebid.server.proto.openrtb.ext.request.feedad;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpFeedAdSdkOptions {

    @JsonProperty("advertising_id")
    String advertisingId;

    @JsonProperty("app_name")
    String appName;

    @JsonProperty("bundle_id")
    String bundleId;

    @JsonProperty("hybrid_app")
    boolean hybridApp;

    @JsonProperty("hybrid_platform")
    String hybridPlatform;

    @JsonProperty("limit_ad_tracking")
    boolean limitAdTracking;
}
