package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class PurposeEid {

    @JsonAlias("activity-transition")
    Boolean activityTransition;

    @JsonAlias("require-consent")
    boolean requireConsent;

    Set<String> exceptions;
}
