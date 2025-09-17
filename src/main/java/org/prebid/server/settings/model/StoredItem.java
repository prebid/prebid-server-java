package org.prebid.server.settings.model;

import lombok.Value;

/**
 * The model helps to reduce multiple rows found for single stored request/imp ID.
 */
@Value(staticConstructor = "of")
<<<<<<< HEAD
public class StoredItem<T> {

    String accountId;

    T data;
=======
public class StoredItem {

    String accountId;

    String data;
>>>>>>> 04d9d4a13 (Initial commit)
}
