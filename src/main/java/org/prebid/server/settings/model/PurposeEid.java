package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class PurposeEid {

    Boolean activityTransition;

    boolean requireConsent;

    Set<String> exceptions;
}
