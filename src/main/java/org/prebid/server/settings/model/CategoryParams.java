package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class CategoryParams {

    String primaryAdServer;
    String publisher;
}
