package org.prebid.server.auction.gpp.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class GppContextWrapper {

    GppContext gppContext;

    List<String> errors;
}
