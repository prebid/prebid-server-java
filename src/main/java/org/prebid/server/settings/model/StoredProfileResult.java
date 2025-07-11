package org.prebid.server.settings.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class StoredProfileResult {

    Map<String, Profile> idToRequestProfile;

    Map<String, Profile> idToImpProfile;

    List<String> errors;
}
