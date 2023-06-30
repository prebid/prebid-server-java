package org.prebid.server.functional.util.privacy.model

enum State {

    //Canada states
    QUEBEC("QC"), ONTARIO("ON"), MANITOBA("MB"),

    //USA states
    ALABAMA("AL"), ALASKA("AK"), ARIZONA("AZ");


    final String abbreviation;

    State(String abbreviation) {
        this.abbreviation = abbreviation
    }

    String getAbbreviation() {
        return abbreviation
    }
}
