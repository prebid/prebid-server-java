package org.rtb.vexing.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class SettingsFile {

    List<String> accounts;

    List<AdUnitConfig> configs;

    List<String> domains;
}
