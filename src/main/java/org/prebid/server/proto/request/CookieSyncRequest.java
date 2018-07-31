package org.prebid.server.proto.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class CookieSyncRequest {

    List<String> bidders;

    Integer gdpr;

    String gdprConsent;
}
