package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

import java.util.Objects;

/**
 * Defines the contract for bidrequest.dooh.ext
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtDooh extends FlexibleExtension {

    private static final ExtDooh EMPTY = ExtDooh.of(null);

    /**
     * Defines the contract for bidrequest.dooh.ext.data.
     */
    ObjectNode data;

    @JsonIgnore
    public boolean isEmpty() {
        return Objects.equals(this, EMPTY);
    }

}
