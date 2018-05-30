package org.prebid.server.settings.proto.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class UpdateSettingsCacheRequest {

    Map<String, String> requests;

    Map<String, String> imps;
}
