package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class RedisBidsData {

    BidRequest breq;

    List<RedisBidResponseData> bresps;

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
