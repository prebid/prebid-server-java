package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

import java.util.List;
import java.util.Objects;

/**
 * Defines the contract for bidrequest.user.ext
 */
@Builder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtUser extends FlexibleExtension {

    private static final ExtUser EMPTY = ExtUser.builder().build();

    ExtUserPrebid prebid;

    /**
     * Consent is a GDPR consent string. See "Advised Extensions" of
     * https://iabtechlab.com/wp-content/uploads/2018/02/OpenRTB_Advisory_GDPR_2018-02.pdf
     */
    String consent;

    /**
     * Standardized User IDs.
     */
    List<ExtUserEid> eids;

    /**
     * List of frequency capped for user.
     */
    @JsonProperty("fcapids")
    List<String> fcapIds;

    /**
     * User date and time.
     */
    ExtUserTime time;

    /**
     * Defines the contract for bidrequest.user.ext.data.
     */
    ObjectNode data;

    /**
     * Defines the contract for bidrequest.user.ext.digitrust
     */
    JsonNode digitrust;

    /**
     * Defines the contract for bidrequest.user.ext.ConsentedProvidersSettings
     */
    @JsonProperty("ConsentedProvidersSettings")
    ConsentedProvidersSettings consentedProvidersSettings;

    @JsonIgnore
    public boolean isEmpty() {
        return Objects.equals(this, EMPTY);
    }
}
