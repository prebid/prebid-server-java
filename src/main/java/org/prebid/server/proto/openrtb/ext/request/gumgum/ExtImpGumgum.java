package org.prebid.server.proto.openrtb.ext.request.gumgum;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigInteger;

@Value(staticConstructor = "of")
public class ExtImpGumgum {

    String zone;

    @JsonProperty("pubId")
    BigInteger pubId;

    @JsonProperty("irisid")
    String irisId;

    Long slot;

    String product;
}
