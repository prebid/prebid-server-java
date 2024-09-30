package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.DsaTransparency;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtBidDsa {

    String behalf;

    String paid;

    List<DsaTransparency> transparency;

    @JsonProperty("adrender")
    Integer adRender;
}
