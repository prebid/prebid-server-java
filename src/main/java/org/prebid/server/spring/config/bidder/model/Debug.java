package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@Validated
@Data
@NoArgsConstructor
public class Debug {

    @NotNull
    Boolean allowed;
}
