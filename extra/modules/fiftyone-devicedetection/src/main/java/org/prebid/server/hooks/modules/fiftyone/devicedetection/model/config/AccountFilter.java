package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import lombok.Data;

import java.util.List;

/**
 * Config fragment to restrict module usage by requesting accounts.
 */
@Data
public final class AccountFilter {
    /**
     * Allow-list with account IDs.
     * @see org.prebid.server.settings.model.Account#getId()
     */
    List<String> allowList;
}
