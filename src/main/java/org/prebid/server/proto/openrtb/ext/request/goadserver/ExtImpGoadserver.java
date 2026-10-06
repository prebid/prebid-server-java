package org.prebid.server.proto.openrtb.ext.request.goadserver;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpGoadserver {

    String host;

    String token;

    BigDecimal floor;

    JsonNode subid;
}
