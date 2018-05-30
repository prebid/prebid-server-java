package org.prebid.server.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class GdprResponse {

    GdprResult gdprResult;
}
