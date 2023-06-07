package org.prebid.server.functional.util.privacy.model;

public enum States {

    //USA states
    ALABAMA("AL"), ALASKA("AK"), ARIZONA("AZ"),

    //Canada states
    QUEBEC("QC"), ONTARIO("ON"), MANITOBA("MB");

    final String abbreviation;

    States(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    public String getAbbreviation() {
        return abbreviation;
    }
}
