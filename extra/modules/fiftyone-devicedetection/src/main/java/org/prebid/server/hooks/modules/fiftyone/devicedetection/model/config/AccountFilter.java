package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import lombok.Data;

import java.util.List;

@Data
public final class AccountFilter {
    List<String> allowList;
}
