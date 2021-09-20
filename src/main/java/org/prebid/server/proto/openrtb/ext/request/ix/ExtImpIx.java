package org.prebid.server.proto.openrtb.ext.request.ix;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpIx {

    @JsonProperty("siteId")
    @JsonAlias({"siteid", "siteID"})
    String siteId;

    List<Integer> size;
}
