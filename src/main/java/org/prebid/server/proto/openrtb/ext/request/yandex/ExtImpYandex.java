package org.prebid.server.proto.openrtb.ext.request.yandex;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYandex {

    Integer pageId;

    Integer impId;
}
