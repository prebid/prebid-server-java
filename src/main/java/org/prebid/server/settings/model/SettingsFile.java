package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class SettingsFile {

    List<String> accounts;

    List<AdUnitConfig> configs;

    List<String> domains;
}
