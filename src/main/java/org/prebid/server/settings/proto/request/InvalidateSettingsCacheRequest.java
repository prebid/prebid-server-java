package org.prebid.server.settings.proto.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class InvalidateSettingsCacheRequest {

    List<String> requests;

    List<String> imps;
}
