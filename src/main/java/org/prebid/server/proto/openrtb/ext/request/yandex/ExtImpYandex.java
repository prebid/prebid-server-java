package org.prebid.server.proto.openrtb.ext.request.yandex;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpYandex {

    Integer pageId;

    Integer impId;
}
