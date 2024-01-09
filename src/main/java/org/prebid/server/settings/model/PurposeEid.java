package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class PurposeEid {

    Boolean activityTransition;

    boolean requireConsent;

    List<String> exceptions;
}
