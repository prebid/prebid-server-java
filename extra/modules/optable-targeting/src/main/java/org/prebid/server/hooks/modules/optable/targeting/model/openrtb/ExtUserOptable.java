package org.prebid.server.hooks.modules.optable.targeting.model.openrtb;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

/**
 * An object containing identifiers
 * which are provided by Optable
 */
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@Value
public class ExtUserOptable extends FlexibleExtension {

    String email;

    String phone;

    String zip;

    String vid;
}
