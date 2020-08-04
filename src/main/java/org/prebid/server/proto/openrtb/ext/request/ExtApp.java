package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

/**
 * Defines the contract for bidrequest.app.ext
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtApp extends FlexibleExtension {

    /**
     * Defines the contract for bidrequest.ext.app.prebid
     */
    ExtAppPrebid prebid;

    /**
     * Defines the contract for bidrequest.app.ext.data.
     */
    ObjectNode data;
}
