package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ConsentedProvidersSettings {

    String consentedProviders;
}
