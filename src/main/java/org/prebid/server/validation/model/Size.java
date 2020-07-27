package org.prebid.server.validation.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class Size {

    Integer width;

    Integer height;
}
