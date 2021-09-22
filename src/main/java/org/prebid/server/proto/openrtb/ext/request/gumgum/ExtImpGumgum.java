package org.prebid.server.proto.openrtb.ext.request.gumgum;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigInteger;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpGumgum {

    String zone;

    @JsonProperty("pubId")
    BigInteger pubId;

    @JsonProperty("irisid")
    String irisId;

    Long slot;
}
