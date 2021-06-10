package org.prebid.server.proto.openrtb.ext.request.interactiveoffers;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.interactiveoffers
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpInteractiveoffers {

    Integer pubid;
}
