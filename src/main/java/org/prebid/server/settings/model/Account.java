package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class Account {

    String id;

    String priceGranularity;
}
