package org.prebid.server.geolocation;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class CountryCodeMapper {

    private final BidiMap<String, String> alpha2ToAlpha3CountryCodes;

    public CountryCodeMapper(String resource) {
        alpha2ToAlpha3CountryCodes = new DualHashBidiMap<>(populateAlpha2ToAlpha3Mapping(resource));
    }

    public String mapToAlpha3(String alpha2Code) {
        return alpha2ToAlpha3CountryCodes.get(StringUtils.upperCase(alpha2Code));
    }

    public String mapToAlpha2(String alpha3Code) {
        return alpha2ToAlpha3CountryCodes.inverseBidiMap().get(StringUtils.upperCase(alpha3Code));
    }

    private Map<String, String> populateAlpha2ToAlpha3Mapping(String countryCodesCsvAsString) {
        return Arrays.stream(countryCodesCsvAsString.split("\n"))
                .map(CountryCodeMapper::parseCountryCodesCsvRow)
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (o1, o2) -> o1));
    }

    private static Pair<String, String> parseCountryCodesCsvRow(String row) {
        final String[] subTokens = row.replaceAll("[^a-zA-Z,]", "").split(",");
        if (subTokens.length != 2 || subTokens[0].length() != 2 || subTokens[1].length() != 3) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid csv file format: row \"%s\" contains more than 2 entries or tokens are invalid",
                            row));
        }

        return Pair.of(subTokens[0].toUpperCase(), subTokens[1].toUpperCase());
    }
}
