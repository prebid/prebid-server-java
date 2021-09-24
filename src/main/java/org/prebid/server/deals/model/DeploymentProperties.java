package org.prebid.server.deals.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class DeploymentProperties {

    String pbsHostId;

    String pbsRegion;

    String pbsVendor;

    String profile;

    String infra;

    String dataCenter;

    String system;

    String subSystem;
}
