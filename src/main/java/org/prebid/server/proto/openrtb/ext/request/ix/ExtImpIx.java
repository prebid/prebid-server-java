package org.prebid.server.proto.openrtb.ext.request.ix;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtImpIx {

    @JsonProperty("siteId")
    @JsonAlias({"siteid", "siteID"})
    String siteId;

    List<Integer> size;

    String sid;
}
