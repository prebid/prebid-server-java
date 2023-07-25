package org.prebid.server.activity.infrastructure.creator.privacy;

import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;

public interface PrivacyModuleCreator {

    PrivacyModuleQualifier qualifier();

    PrivacyModule from(PrivacyModuleCreationContext creationContext);
}
