package org.prebid.server.bidder.mobkoi.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class MobkoiBidRequestExt {

    @JsonProperty("mobkoi")
    final Mobkoi mobkoi;

    public static MobkoiBidRequestExt of() {
        return new MobkoiBidRequestExt(Mobkoi.of());
    }

    @Value(staticConstructor = "of")
    public static class Mobkoi {

        @JsonProperty("integration_type")
        final String integrationType = "pbs";
    }
}
