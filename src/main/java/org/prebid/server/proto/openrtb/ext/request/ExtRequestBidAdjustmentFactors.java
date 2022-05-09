package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Value(staticConstructor = "of")
@Builder(toBuilder = true)
public class ExtRequestBidAdjustmentFactors {

    Map<String, BigDecimal> adjustments = new HashMap<>();

    EnumMap<ImpMediaType, Map<String, BigDecimal>> mediatypes;

    @JsonAnyGetter
    public Map<String, BigDecimal> getAdjustments() {
        return Collections.unmodifiableMap(adjustments);
    }

    @JsonAnySetter
    public void addFactor(String key, BigDecimal value) {
        adjustments.put(key, value);
    }
}
