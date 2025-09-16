package org.prebid.server.bidder.alvads.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class AlvaAdsSite {

    private String page;
    private String ref;
    private Map<String, Object> publisher;
}
