package org.prebid.server.proto.openrtb.ext.request.sharethrough;

import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidRequest.imp[i].ext.sharethrough
 */
@Value(staticConstructor = "of")
public class ExtImpSharethrough {

    String pkey;

    List<String> badv;

    List<String> bcat;
}
