package org.prebid.server.geolocation;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CountryCodeMapper {

    private final Map<String, String> alpha2ToAlpha3CountryCodes;
    private final Map<String, String> alpha3ToAlpha2CountryCodes;
    private final Map<String, String> mccToAlpha2CountryCodes;

    public CountryCodeMapper(String countryCodesCsv, String mccCountryCodesCsv) {
        final List<Pair<String, String>> countryCodes = populateAlpha2ToAlpha3Mapping(countryCodesCsv);
        alpha2ToAlpha3CountryCodes = countryCodes.stream()
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (o1, o2) -> o1));
        alpha3ToAlpha2CountryCodes = countryCodes.stream()
                .collect(Collectors.toMap(Pair::getValue, Pair::getKey, (o1, o2) -> o2));
        mccToAlpha2CountryCodes = populateMccToAlpha2Mapping(mccCountryCodesCsv);
    }

    public String mapToAlpha3(String alpha2Code) {
        return alpha2ToAlpha3CountryCodes.get(StringUtils.upperCase(alpha2Code));
    }

    public String mapToAlpha2(String alpha3Code) {
        return alpha3ToAlpha2CountryCodes.get(StringUtils.upperCase(alpha3Code));
    }

    public String mapMccToAlpha2(String mcc) {
        return mccToAlpha2CountryCodes.get(mcc);
    }

    private List<Pair<String, String>> populateAlpha2ToAlpha3Mapping(String countryCodesCsvAsString) {
        return Arrays.stream(countryCodesCsvAsString.split("\n"))
                .map(CountryCodeMapper::parseCountryCodesCsvRow)
                .toList();
    }

    private static Pair<String, String> parseCountryCodesCsvRow(String row) {
        final String[] subTokens = row.replaceAll("[^a-zA-Z,]", "").split(",");
        if (subTokens.length != 2 || subTokens[0].length() != 2 || subTokens[1].length() != 3) {
            throw new IllegalArgumentException(
                    "Invalid csv file format: row \"%s\" contains more than 2 entries or tokens are invalid"
                            .formatted(row));
        }

        return Pair.of(subTokens[0].toUpperCase(), subTokens[1].toUpperCase());
    }

    private Map<String, String> populateMccToAlpha2Mapping(String csv) {
        return Arrays.stream(csv.split("\n"))
                .map(CountryCodeMapper::parseMccCountryCodesCsvRow)
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (o1, o2) -> o1));
    }

    private static Pair<String, String> parseMccCountryCodesCsvRow(String row) {
        final String[] subTokens = row.replaceAll("[^0-9a-zA-Z,]", "").split(",");
        if (subTokens.length != 2 || subTokens[0].length() != 3 || subTokens[1].length() != 2) {
            throw new IllegalArgumentException(
                    "Invalid csv file format: row \"%s\" contains more than 2 entries or tokens are invalid"
                            .formatted(row));
        }

        return Pair.of(subTokens[0], subTokens[1].toUpperCase());
    }
}
