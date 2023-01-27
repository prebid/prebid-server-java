package org.prebid.server.proto.openrtb.ext.request.intertech;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpIntertech {

    Integer pageId;

    Integer impId;
}
