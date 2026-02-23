package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.app.prebid
 * We are only enforcing that these two properties be strings if they are provided.
 * They are optional with no current constraints on value.
 */
@Value(staticConstructor = "of")
public class ExtAppPrebid {

    String source;

    String version;
}
