package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.UsersyncMethodType;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Validated
@Data
@NoArgsConstructor
public class UsersyncConfigurationProperties {

    @NotBlank
    String cookieFamilyName;

    List<UsersyncMethodConfigurationProperties> methods;

    @Data
    @NoArgsConstructor
    public static class UsersyncMethodConfigurationProperties {

        String url;

        String uidMacro;

        @NotNull
        UsersyncMethodType type;

        @NotNull
        Boolean supportCors;
    }
}
