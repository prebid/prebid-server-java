package org.prebid.server.proto.openrtb.ext.request;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

/**
 * Defines the contract for bidrequest.ext.prebid.analytics[i]
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtRequestPrebidAnalytic extends FlexibleExtension {

    String adapter;
}
