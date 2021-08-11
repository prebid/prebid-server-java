package org.prebid.server.spring.config.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
@AllArgsConstructor
public class Debug {

    @NotNull
    Boolean allowed;
}
