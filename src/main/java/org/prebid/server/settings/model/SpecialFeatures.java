package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialFeatures {

    SpecialFeature sf1;

    SpecialFeature sf2;

    SpecialFeature sf3;

    SpecialFeature sf4;

    SpecialFeature sf5;
}

