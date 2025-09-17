package org.prebid.server.settings.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
<<<<<<< HEAD
public class StoredDataResult<T> {

    Map<String, T> storedIdToRequest;

    Map<String, T> storedIdToImp;
=======
public class StoredDataResult {

    Map<String, String> storedIdToRequest;

    Map<String, String> storedIdToImp;
>>>>>>> 04d9d4a13 (Initial commit)

    List<String> errors;
}
