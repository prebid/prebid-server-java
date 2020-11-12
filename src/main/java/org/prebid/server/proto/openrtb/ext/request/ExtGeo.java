package org.prebid.server.proto.openrtb.ext.request;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtGeo extends FlexibleExtension {
}
