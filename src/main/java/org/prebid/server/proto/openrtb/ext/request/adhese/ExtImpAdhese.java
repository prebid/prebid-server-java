package org.prebid.server.proto.openrtb.ext.request.adhese;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adhese
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdhese {

    String account;

    String location;

    String format;

    JsonNode targets;
}
