package org.prebid.server.settings.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class DefaultDsa {

    Integer required;

    Integer pubrender;

    Integer datatopub;

    List<DsaTransparency> transparency;
}
