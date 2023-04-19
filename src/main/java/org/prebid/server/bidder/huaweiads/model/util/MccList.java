package org.prebid.server.bidder.huaweiads.model.util;

import java.util.HashMap;
import java.util.Map;

public class MccList {

    public static final Map<Integer, String> mccMap;

    static {
        mccMap = new HashMap<>();
        mccMap.put(202, "gr"); //Greece
        mccMap.put(204, "nl"); //Netherlands (Kingdom of the)
        mccMap.put(206, "be"); //Belgium
        mccMap.put(208, "fr"); //France
        mccMap.put(212, "mc"); //Monaco (Principality of)
        mccMap.put(213, "ad"); //Andorra (Principality of)
        mccMap.put(214, "es"); //Spain
        mccMap.put(216, "hu"); //Hungary (Republic of)
        mccMap.put(218, "ba"); //Bosnia and Herzegovina
        mccMap.put(219, "hr"); //Croatia (Republic of)
        mccMap.put(220, "rs"); //Serbia and Montenegro
        mccMap.put(222, "it"); //Italy
        mccMap.put(225, "va"); //Vatican City State
        mccMap.put(226, "ro"); //Romania
        mccMap.put(228, "ch"); //Switzerland (Confederation of)
        mccMap.put(230, "cz"); //Czech Republic
        mccMap.put(231, "sk"); //Slovak Republic
        mccMap.put(232, "at"); //Austria
        mccMap.put(234, "gb"); //United Kingdom of Great Britain and Northern Ireland
        mccMap.put(235, "gb"); //United Kingdom of Great Britain and Northern Ireland
        mccMap.put(238, "dk"); //Denmark
        mccMap.put(240, "se"); //Sweden
        mccMap.put(242, "no"); //Norway
        mccMap.put(244, "fi"); //Finland
        mccMap.put(246, "lt"); //Lithuania (Republic of)
        mccMap.put(247, "lv"); //Latvia (Republic of)
        mccMap.put(248, "ee"); //Estonia (Republic of)
        mccMap.put(250, "ru"); //Russian Federation
        mccMap.put(255, "ua"); //Ukraine
        mccMap.put(257, "by"); //Belarus (Republic of)
        mccMap.put(259, "md"); //Moldova (Republic of)
        mccMap.put(260, "pl"); //Poland (Republic of)
        mccMap.put(262, "de"); //Germany (Federal Republic of)
        mccMap.put(266, "gi"); //Gibraltar
        mccMap.put(268, "pt"); //Portugal
        mccMap.put(270, "lu"); //Luxembourg
        mccMap.put(272, "ie"); //Ireland
        mccMap.put(274, "is"); //Iceland
        mccMap.put(276, "al"); //Albania (Republic of)
        mccMap.put(278, "mt"); //Malta
        mccMap.put(280, "cy"); //Cyprus (Republic of)
        mccMap.put(282, "ge"); //Georgia
        mccMap.put(283, "am"); //Armenia (Republic of)
        mccMap.put(284, "bg"); //Bulgaria (Republic of)
    }

}
