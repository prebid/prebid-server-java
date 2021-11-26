package org.prebid.server.geolocation;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CountryCodeMapper {

    private final Map<String, String> alpha2ToAlpha3CountryCodes;
    private final Map<String, String> alpha3ToAlpha2CountryCodes;

    public CountryCodeMapper(String resource) {
        alpha2ToAlpha3CountryCodes = new HashMap<>();
        alpha3ToAlpha2CountryCodes = new HashMap<>();

        populateCountryCodesMappings(resource);
    }

    public String mapToAlpha3(String alpha2Code) {
        return StringUtils.isNotEmpty(alpha2Code)
                ? alpha2ToAlpha3CountryCodes.get(alpha2Code.toUpperCase())
                : null;
    }

    public String mapToAlpha2(String alpha3Code) {
        return StringUtils.isNotEmpty(alpha3Code)
                ? alpha3ToAlpha2CountryCodes.get(alpha3Code.toUpperCase())
                : null;
    }

    private void populateCountryCodesMappings(String countryCodesCsvAsString) {
        Arrays.stream(countryCodesCsvAsString.split("\n"))
                .map(CountryCodeMapper::parseCountryCodesCsvRow)
                .forEach(this::putCountryCodeMapping);
    }

    private static Pair<String, String> parseCountryCodesCsvRow(String row) {
        String[] subTokens = row.replaceAll("[^a-zA-Z,.]", "").split(",");
        if (subTokens.length != 2 || subTokens[0].length() != 2 || subTokens[1].length() != 3) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid csv file format: row \"%s\" contains more than 2 entries or tokens are invalid",
                            row));
        }

        return Pair.of(subTokens[0].toUpperCase(), subTokens[1].toUpperCase());
    }

    private void putCountryCodeMapping(Pair<String, String> alpha2ToAlpha3Mapping) {
        alpha2ToAlpha3CountryCodes.put(alpha2ToAlpha3Mapping.getLeft(), alpha2ToAlpha3Mapping.getRight());
        alpha3ToAlpha2CountryCodes.put(alpha2ToAlpha3Mapping.getRight(), alpha2ToAlpha3Mapping.getLeft());
    }
}
