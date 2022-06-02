package org.prebid.server.proto.openrtb.ext.request;

import com.iab.openrtb.request.SupplyChain;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtSource extends FlexibleExtension {

    SupplyChain schain;
}

