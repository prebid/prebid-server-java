package org.prebid.server.settings.proto.request;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class UpdateSettingsCacheRequest {

    Map<String, String> requests;

    Map<String, String> imps;
}
