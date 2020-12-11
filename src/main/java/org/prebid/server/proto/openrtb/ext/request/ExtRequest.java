package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

import java.util.Map;

/**
 * Defines the contract for bidrequest.ext
 */
@Value
@RequiredArgsConstructor(staticName = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtRequest extends FlexibleExtension {

    /**
     * Defines the contract for bidrequest.ext.prebid
     */
    ExtRequestPrebid prebid;

    private ExtRequest(Map<String, JsonNode> properties, ExtRequestPrebid prebid) {
        super(properties);
        this.prebid = prebid;
    }

    public static ExtRequest of(Map<String, JsonNode> properties, ExtRequestPrebid prebid) {
        return new ExtRequest(properties, prebid);
    }

    public static ExtRequest empty() {
        return of(null);
    }
}
