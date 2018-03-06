package org.prebid.server.settings.model;

import lombok.Value;

import java.util.List;

@Value
public class SettingsFile {

    List<String> accounts;

    List<AdUnitConfig> configs;

    List<String> domains;
}
