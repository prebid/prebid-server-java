package org.prebid.server.settings.proto.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class InvalidateSettingsCacheRequest {

    List<String> requests;

    List<String> imps;
}
