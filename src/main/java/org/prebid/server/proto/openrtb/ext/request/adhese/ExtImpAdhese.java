package org.prebid.server.proto.openrtb.ext.request.adhese;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adhese
 */
@Value(staticConstructor = "of")
public class ExtImpAdhese {

    String account;

    String location;

    String format;

    JsonNode targets;
}
