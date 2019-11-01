package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class ParsedStoredDataResult<R, I> {

    R storedData;

    Map<String, I> idToimps;

    List<String> errors;
}
