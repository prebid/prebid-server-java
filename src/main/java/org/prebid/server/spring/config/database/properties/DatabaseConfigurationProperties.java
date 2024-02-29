package org.prebid.server.spring.config.database.properties;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.spring.config.database.model.DatabasePoolType;
import org.prebid.server.spring.config.database.model.DatabaseType;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class DatabaseConfigurationProperties {

    @NotNull
    private DatabaseType type;
    @NotNull
    @Min(1)
    private Integer poolSize;
    @NotBlank
    private String host;
    @NotNull
    private Integer port;
    @NotBlank
    private String dbname;
    @NotBlank
    private String user;
    @NotBlank
    private String password;
    @NotNull
    private DatabasePoolType providerClass;
}

