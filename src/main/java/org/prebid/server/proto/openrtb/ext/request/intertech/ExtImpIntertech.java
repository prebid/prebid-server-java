package org.prebid.server.proto.openrtb.ext.request.intertech;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpIntertech {

    Integer pageId;

    Integer impId;
}
