package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

/**
 * Defines the contract for bidrequest.site.ext
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtSite extends FlexibleExtension {

    /**
     * AMP should be 1 if the request comes from an AMP page, and 0 if not.
     */
    Integer amp;

    /**
     * Defines the contract for bidrequest.site.ext.data.
     */
    ObjectNode data;
}
