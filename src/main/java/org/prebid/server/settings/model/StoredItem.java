package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * The model helps to reduce multiple rows found for single stored request/imp ID.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class StoredItem {

    String accountId;

    String data;
}
