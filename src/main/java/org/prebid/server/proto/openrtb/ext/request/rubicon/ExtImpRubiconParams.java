package org.prebid.server.proto.openrtb.ext.request.rubicon;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.util.Set;

@Value(staticConstructor = "of")
public class ExtImpRubiconParams {

    Set<ImpMediaType> formats;
}
