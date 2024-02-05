package org.prebid.server.settings.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class DsaTransparency {

    String domain;

    List<Integer> params;
}
