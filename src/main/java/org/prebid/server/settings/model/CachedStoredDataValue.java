package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class CachedStoredDataValue {

    String accountId;

    String value;
}
