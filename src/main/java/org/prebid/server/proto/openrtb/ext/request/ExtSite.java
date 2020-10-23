package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

import java.util.Objects;

/**
 * Defines the contract for bidrequest.site.ext
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtSite extends FlexibleExtension {

    private static final ExtSite EMPTY = ExtSite.of(null, null);

    /**
     * AMP should be 1 if the request comes from an AMP page, and 0 if not.
     */
    Integer amp;

    /**
     * Defines the contract for bidrequest.site.ext.data.
     */
    ObjectNode data;

    @JsonIgnore
    public boolean isEmpty() {
        return Objects.equals(this, EMPTY);
    }
}
