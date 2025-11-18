package org.prebid.server.settings.model;

import lombok.Value;

/**
 * The model helps to reduce multiple rows found for single stored request/imp ID.
 */
@Value(staticConstructor = "of")
public class StoredItem<T> {

    String accountId;

    T data;
}
