package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.privacy.model.Privacy;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtResponseDebugPrivacy {
    Privacy originPrivacy;

    Privacy resolvedPrivacy;

    Integer tcfConsentVersion;

    Map<String, List<String>> biddersLogs;

    List<String> errors;
}
