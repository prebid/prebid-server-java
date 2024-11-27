package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class AccountHooksAdminConfig {

    @JsonAlias("module-execution")
    Map<String, Boolean> moduleExecution;

}
