package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.context.adserver
 */
@Value(staticConstructor = "of")
public class ExtImpContextAdserver {

    String name;

    String adslot;
}
