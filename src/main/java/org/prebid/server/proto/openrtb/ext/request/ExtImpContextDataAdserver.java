package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.context.data.adserver
 */
@Value(staticConstructor = "of")
public class ExtImpContextDataAdserver {

    String name;

    String adslot;
}
