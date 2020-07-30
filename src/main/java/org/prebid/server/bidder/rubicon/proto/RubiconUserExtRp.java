package org.prebid.server.bidder.rubicon.proto;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Geo;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RubiconUserExtRp extends FlexibleExtension {

    JsonNode target;

    String gender;

    Integer yob;

    Geo geo;
}
