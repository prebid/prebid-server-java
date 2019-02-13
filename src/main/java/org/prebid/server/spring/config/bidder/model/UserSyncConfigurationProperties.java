package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@NoArgsConstructor
public class UserSyncConfigurationProperties {
    String url;

    String type;

    Boolean supportCors;
}
