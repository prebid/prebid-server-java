package org.prebid.server.proto.openrtb.ext.request.smaato;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.smaato
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSmaato {

    String publisherId;

    String adspaceId;
}
