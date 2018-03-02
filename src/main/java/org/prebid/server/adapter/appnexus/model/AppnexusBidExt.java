package org.prebid.server.adapter.appnexus.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class AppnexusBidExt {

    AppnexusBidExtAppnexus appnexus;
}
