package org.prebid.server.activity.infrastructure.creator;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.settings.model.activity.privacy.AccountPrivacyModuleConfig;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Value(staticConstructor = "of")
public class ActivityControllerCreationContext {

    Activity activity;

    Map<PrivacyModuleQualifier, AccountPrivacyModuleConfig> privacyModulesConfigs;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    Set<PrivacyModuleQualifier> usedPrivacyModules = EnumSet.noneOf(PrivacyModuleQualifier.class);

    GppContext gppContext;

    public boolean isUsed(PrivacyModuleQualifier qualifier) {
        return usedPrivacyModules.contains(qualifier);
    }

    public void use(PrivacyModuleQualifier qualifier) {
        usedPrivacyModules.add(qualifier);
    }
}
