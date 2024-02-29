package org.prebid.server.activity.infrastructure.creator;

import lombok.Value;
import org.prebid.server.activity.Activity;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.settings.model.activity.privacy.AccountPrivacyModuleConfig;

@Value(staticConstructor = "of")
public class PrivacyModuleCreationContext {

    Activity activity;

    AccountPrivacyModuleConfig privacyModuleConfig;

    GppContext gppContext;
}
