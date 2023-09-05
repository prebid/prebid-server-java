package org.prebid.server.bidder.huaweiads;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class CountryCodeResolver {

    private static final Map<String, String> ALPHA3_TO_ALPHA2_COUNTRY_CODE_MAP = createAlpha3ToAlpha2CountryCodeMap();
    private static final Map<Integer, String> MCC_TO_COUNTRY_CODE_MAP = createMccToAlpha2CountryCodeMap();

    private CountryCodeResolver() {

    }

    //expects Alpha3 country code
    public static Optional<String> resolve(BidRequest bidRequest) {
        final Optional<String> countryOfDevice = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getGeo)
                .map(Geo::getCountry)
                .filter(StringUtils::isNotBlank)
                .filter(countryCode -> countryCode.length() >= 2)
                .map(CountryCodeResolver::convertAlpha3ToAlpha2CountryCode);

        final Optional<String> countryOfUser = Optional.ofNullable(bidRequest.getUser())
                .map(User::getGeo)
                .map(Geo::getCountry)
                .filter(StringUtils::isNotBlank)
                .filter(countryCode -> countryCode.length() >= 2)
                .map(CountryCodeResolver::convertAlpha3ToAlpha2CountryCode);

        final Optional<String> countryOfMcc = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getMccmnc)
                .filter(StringUtils::isNotBlank)
                //mcc-mnc format
                //todo: what if out of bound
                .map(mccmnc -> mccmnc.split("-")[0])
                .flatMap(CountryCodeResolver::countryFromMcc);

        //todo: logic is slighty enhanced comparing to the PBS Go,
        // if a country code of the device is invalid (has less than 3 symbols) I try to get the country from the user
        // if the country of the user is invalid (has less than 3 symbols) I try to get the country from the MCC
        // PBS Go returns default country code instead without trying to try to resolve code further
        return countryOfDevice
                .or(() -> countryOfUser)
                .or(() -> countryOfMcc);
    }

    // convertCountryCode: ISO 3166-1 Alpha3 -> Alpha2, Some countries may use
    private static String convertAlpha3ToAlpha2CountryCode(String countryCode) {
        //todo: taking first two letters are not robust; is case ignored?
        return ALPHA3_TO_ALPHA2_COUNTRY_CODE_MAP.getOrDefault(countryCode, countryCode.substring(0, 2));
    }

    private static Optional<String> countryFromMcc(String mcc) {
        try {
            return Optional.ofNullable(MCC_TO_COUNTRY_CODE_MAP.get(Integer.parseInt(mcc)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Map<String, String> createAlpha3ToAlpha2CountryCodeMap() {
        final Map<String, String> result = new HashMap<>();
        result.put("AND", "AD");
        result.put("AGO", "AO");
        result.put("AUT", "AT");
        result.put("BGD", "BD");
        result.put("BLR", "BY");
        result.put("CAF", "CF");
        result.put("TCD", "TD");
        result.put("CHL", "CL");
        result.put("CHN", "CN");
        result.put("COG", "CG");
        result.put("COD", "CD");
        result.put("DNK", "DK");
        result.put("GNQ", "GQ");
        result.put("EST", "EE");
        result.put("GIN", "GN");
        result.put("GNB", "GW");
        result.put("GUY", "GY");
        result.put("IRQ", "IQ");
        result.put("IRL", "IE");
        result.put("ISR", "IL");
        result.put("KAZ", "KZ");
        result.put("LBY", "LY");
        result.put("MDG", "MG");
        result.put("MDV", "MV");
        result.put("MEX", "MX");
        result.put("MNE", "ME");
        result.put("MOZ", "MZ");
        result.put("PAK", "PK");
        result.put("PNG", "PG");
        result.put("PRY", "PY");
        result.put("POL", "PL");
        result.put("PRT", "PT");
        result.put("SRB", "RS");
        result.put("SVK", "SK");
        result.put("SVN", "SI");
        result.put("SWE", "SE");
        result.put("TUN", "TN");
        result.put("TUR", "TR");
        result.put("TKM", "TM");
        result.put("UKR", "UA");
        result.put("ARE", "AE");
        result.put("URY", "UY");
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("MethodLength")
    private static Map<Integer, String> createMccToAlpha2CountryCodeMap() {
        final Map<Integer, String> result = new HashMap<>();
        result.put(202, "GR"); //Greece
        result.put(204, "NL"); //Netherlands (Kingdom of the)
        result.put(206, "BE"); //Belgium
        result.put(208, "FR"); //France
        result.put(212, "MC"); //Monaco (Principality of)
        result.put(213, "AD"); //Andorra (Principality of)
        result.put(214, "ES"); //Spain
        result.put(216, "HU"); //Hungary (Republic of)
        result.put(218, "BA"); //Bosnia and Herzegovina
        result.put(219, "HR"); //Croatia (Republic of)
        result.put(220, "RS"); //Serbia and Montenegro
        result.put(222, "IT"); //Italy
        result.put(225, "VA"); //Vatican City State
        result.put(226, "RO"); //Romania
        result.put(228, "CH"); //Switzerland (Confederation of)
        result.put(230, "CZ"); //Czech Republic
        result.put(231, "SK"); //Slovak Republic
        result.put(232, "AT"); //Austria
        result.put(234, "GB"); //United Kingdom of Great Britain and Northern Ireland
        result.put(235, "GB"); //United Kingdom of Great Britain and Northern Ireland
        result.put(238, "DK"); //Denmark
        result.put(240, "SE"); //Sweden
        result.put(242, "NO"); //Norway
        result.put(244, "FI"); //Finland
        result.put(246, "LT"); //Lithuania (Republic of)
        result.put(247, "LV"); //Latvia (Republic of)
        result.put(248, "EE"); //Estonia (Republic of)
        result.put(250, "RU"); //Russian Federation
        result.put(255, "UA"); //Ukraine
        result.put(257, "BY"); //Belarus (Republic of)
        result.put(259, "MD"); //Moldova (Republic of)
        result.put(260, "PL"); //Poland (Republic of)
        result.put(262, "DE"); //Germany (Federal Republic of)
        result.put(266, "GI"); //Gibraltar
        result.put(268, "PT"); //Portugal
        result.put(270, "LU"); //Luxembourg
        result.put(272, "IE"); //Ireland
        result.put(274, "IS"); //Iceland
        result.put(276, "AL"); //Albania (Republic of)
        result.put(278, "MT"); //Malta
        result.put(280, "CY"); //Cyprus (Republic of)
        result.put(282, "GE"); //Georgia
        result.put(283, "AM"); //Armenia (Republic of)
        result.put(284, "BG"); //Bulgaria (Republic of)
        result.put(286, "TR"); //Turkey
        result.put(288, "FO"); //Faroe Islands
        result.put(289, "GE"); //Abkhazia (Georgia)
        result.put(290, "GL"); //Greenland (Denmark)
        result.put(292, "SM"); //San Marino (Republic of)
        result.put(293, "SI"); //Slovenia (Republic of)
        result.put(294, "MK"); //The Former Yugoslav Republic of Macedonia
        result.put(295, "LI"); //Liechtenstein (Principality of)
        result.put(297, "ME"); //Montenegro (Republic of)
        result.put(302, "CA"); //Canada
        result.put(308, "PM"); //Saint Pierre and Miquelon (Collectivit territoriale de la Rpublique franaise)
        result.put(310, "US"); //United States of America
        result.put(311, "US"); //United States of America
        result.put(312, "US"); //United States of America
        result.put(313, "US"); //United States of America
        result.put(314, "US"); //United States of America
        result.put(315, "US"); //United States of America
        result.put(316, "US"); //United States of America
        result.put(330, "PR"); //Puerto Rico
        result.put(332, "VI"); //United States Virgin Islands
        result.put(334, "MX"); //Mexico
        result.put(338, "JM"); //Jamaica
        result.put(340, "GP"); //Guadeloupe (French Department of)
        result.put(342, "BB"); //Barbados
        result.put(344, "AG"); //Antigua and Barbuda
        result.put(346, "KY"); //Cayman Islands
        result.put(348, "VG"); //British Virgin Islands
        result.put(350, "BM"); //Bermuda
        result.put(352, "GD"); //Grenada
        result.put(354, "MS"); //Montserrat
        result.put(356, "KN"); //Saint Kitts and Nevis
        result.put(358, "LC"); //Saint Lucia
        result.put(360, "VC"); //Saint Vincent and the Grenadines
        result.put(362, "AI"); //Netherlands Antilles
        result.put(363, "AW"); //Aruba
        result.put(364, "BS"); //Bahamas (Commonwealth of the)
        result.put(365, "AI"); //Anguilla
        result.put(366, "DM"); //Dominica (Commonwealth of)
        result.put(368, "CU"); //Cuba
        result.put(370, "DO"); //Dominican Republic
        result.put(372, "HT"); //Haiti (Republic of)
        result.put(374, "TT"); //Trinidad and Tobago
        result.put(376, "TC"); //Turks and Caicos Islands
        result.put(400, "AZ"); //Azerbaijani Republic
        result.put(401, "KZ"); //Kazakhstan (Republic of)
        result.put(402, "BT"); //Bhutan (Kingdom of)
        result.put(404, "IN"); //India (Republic of)
        result.put(405, "IN"); //India (Republic of)
        result.put(406, "IN"); //India (Republic of)
        result.put(410, "PK"); //Pakistan (Islamic Republic of)
        result.put(412, "AF"); //Afghanistan
        result.put(413, "LK"); //Sri Lanka (Democratic Socialist Republic of)
        result.put(414, "MM"); //Myanmar (Union of)
        result.put(415, "LB"); //Lebanon
        result.put(416, "JO"); //Jordan (Hashemite Kingdom of)
        result.put(417, "SY"); //Syrian Arab Republic
        result.put(418, "IQ"); //Iraq (Republic of)
        result.put(419, "KW"); //Kuwait (State of)
        result.put(420, "SA"); //Saudi Arabia (Kingdom of)
        result.put(421, "YE"); //Yemen (Republic of)
        result.put(422, "OM"); //Oman (Sultanate of)
        result.put(423, "PS"); //Palestine
        result.put(424, "AE"); //United Arab Emirates
        result.put(425, "IL"); //Israel (State of)
        result.put(426, "BH"); //Bahrain (Kingdom of)
        result.put(427, "QA"); //Qatar (State of)
        result.put(428, "MN"); //Mongolia
        result.put(429, "NP"); //Nepal
        result.put(430, "AE"); //United Arab Emirates
        result.put(431, "AE"); //United Arab Emirates
        result.put(432, "IR"); //Iran (Islamic Republic of)
        result.put(434, "UZ"); //Uzbekistan (Republic of)
        result.put(436, "TJ"); //Tajikistan (Republic of)
        result.put(437, "KG"); //Kyrgyz Republic
        result.put(438, "TM"); //Turkmenistan
        result.put(440, "JP"); //Japan
        result.put(441, "JP"); //Japan
        result.put(450, "KR"); //Korea (Republic of)
        result.put(452, "VN"); //VietNam (Socialist Republic of)
        result.put(454, "HK"); //"Hong Kong, China"
        result.put(455, "MO"); //"Macao, China"
        result.put(456, "KH"); //Cambodia (Kingdom of)
        result.put(457, "LA"); //Lao People's Democratic Republic
        result.put(460, "CN"); //China (People's Republic of)
        result.put(461, "CN"); //China (People's Republic of)
        result.put(466, "TW"); //"Taiwan, China"
        result.put(467, "KP"); //Democratic People's Republic of Korea
        result.put(470, "BD"); //Bangladesh (People's Republic of)
        result.put(472, "MV"); //Maldives (Republic of)
        result.put(502, "MY"); //Malaysia
        result.put(505, "AU"); //Australia
        result.put(510, "ID"); //Indonesia (Republic of)
        result.put(514, "TL"); //Democratic Republic of Timor-Leste
        result.put(515, "PH"); //Philippines (Republic of the)
        result.put(520, "TH"); //Thailand
        result.put(525, "SG"); //Singapore (Republic of)
        result.put(528, "BN"); //Brunei Darussalam
        result.put(530, "NZ"); //New Zealand
        result.put(534, "MP"); //Northern Mariana Islands (Commonwealth of the)
        result.put(535, "GU"); //Guam
        result.put(536, "NR"); //Nauru (Republic of)
        result.put(537, "PG"); //Papua New Guinea
        result.put(539, "TO"); //Tonga (Kingdom of)
        result.put(540, "SB"); //Solomon Islands
        result.put(541, "VU"); //Vanuatu (Republic of)
        result.put(542, "FJ"); //Fiji (Republic of)
        result.put(543, "WF"); //Wallis and Futuna (Territoire franais d'outre-mer)
        result.put(544, "AS"); //American Samoa
        result.put(545, "KI"); //Kiribati (Republic of)
        result.put(546, "NC"); //New Caledonia (Territoire franais d'outre-mer)
        result.put(547, "PF"); //French Polynesia (Territoire franais d'outre-mer)
        result.put(548, "CK"); //Cook Islands
        result.put(549, "WS"); //Samoa (Independent State of)
        result.put(550, "FM"); //Micronesia (Federated States of)
        result.put(551, "MH"); //Marshall Islands (Republic of the)
        result.put(552, "PW"); //Palau (Republic of)
        result.put(553, "TV"); //Tuvalu
        result.put(555, "NU"); //Niue
        result.put(602, "EG"); //Egypt (Arab Republic of)
        result.put(603, "DZ"); //Algeria (People's Democratic Republic of)
        result.put(604, "MA"); //Morocco (Kingdom of)
        result.put(605, "TN"); //Tunisia
        result.put(606, "LY"); //Libya (Socialist People's Libyan Arab Jamahiriya)
        result.put(607, "GM"); //Gambia (Republic of the)
        result.put(608, "SN"); //Senegal (Republic of)
        result.put(609, "MR"); //Mauritania (Islamic Republic of)
        result.put(610, "ML"); //Mali (Republic of)
        result.put(611, "GN"); //Guinea (Republic of)
        result.put(612, "CI"); //Côte d'Ivoire (Republic of)
        result.put(613, "BF"); //Burkina Faso
        result.put(614, "NE"); //Niger (Republic of the)
        result.put(615, "TG"); //Togolese Republic
        result.put(616, "BJ"); //Benin (Republic of)
        result.put(617, "MU"); //Mauritius (Republic of)
        result.put(618, "LR"); //Liberia (Republic of)
        result.put(619, "SL"); //Sierra Leone
        result.put(620, "GH"); //Ghana
        result.put(621, "NG"); //Nigeria (Federal Republic of)
        result.put(622, "TD"); //Chad (Republic of)
        result.put(623, "CF"); //Central African Republic
        result.put(624, "CM"); //Cameroon (Republic of)
        result.put(625, "CV"); //Cape Verde (Republic of)
        result.put(626, "ST"); //Sao Tome and Principe (Democratic Republic of)
        result.put(627, "GQ"); //Equatorial Guinea (Republic of)
        result.put(628, "GA"); //Gabonese Republic
        result.put(629, "CG"); //Congo (Republic of the)
        result.put(630, "CG"); //Democratic Republic of the Congo
        result.put(631, "AO"); //Angola (Republic of)
        result.put(632, "GW"); //Guinea-Bissau (Republic of)
        result.put(633, "SC"); //Seychelles (Republic of)
        result.put(634, "SD"); //Sudan (Republic of the)
        result.put(635, "RW"); //Rwanda (Republic of)
        result.put(636, "ET"); //Ethiopia (Federal Democratic Republic of)
        result.put(637, "SO"); //Somali Democratic Republic
        result.put(638, "DJ"); //Djibouti (Republic of)
        result.put(639, "KE"); //Kenya (Republic of)
        result.put(640, "TZ"); //Tanzania (United Republic of)
        result.put(641, "UG"); //Uganda (Republic of)
        result.put(642, "BI"); //Burundi (Republic of)
        result.put(643, "MZ"); //Mozambique (Republic of)
        result.put(645, "ZM"); //Zambia (Republic of)
        result.put(646, "MG"); //Madagascar (Republic of)
        result.put(647, "RE"); //Reunion (French Department of)
        result.put(648, "ZW"); //Zimbabwe (Republic of)
        result.put(649, "NA"); //Namibia (Republic of)
        result.put(650, "MW"); //Malawi
        result.put(651, "LS"); //Lesotho (Kingdom of)
        result.put(652, "BW"); //Botswana (Republic of)
        result.put(653, "SZ"); //Swaziland (Kingdom of)
        result.put(654, "KM"); //Comoros (Union of the)
        result.put(655, "ZA"); //South Africa (Republic of)
        result.put(657, "ER"); //Eritrea
        result.put(658, "SH"); //Saint Helena, Ascension and Tristan da Cunha
        result.put(659, "SS"); //South Sudan (Republic of)
        result.put(702, "BZ"); //Belize
        result.put(704, "GT"); //Guatemala (Republic of)
        result.put(706, "SV"); //El Salvador (Republic of)
        result.put(708, "HN"); //Honduras (Republic of)
        result.put(710, "NI"); //Nicaragua
        result.put(712, "CR"); //Costa Rica
        result.put(714, "PA"); //Panama (Republic of)
        result.put(716, "PE"); //Peru
        result.put(722, "AR"); //Argentine Republic
        result.put(724, "BR"); //Brazil (Federative Republic of)
        result.put(730, "CL"); //Chile
        result.put(732, "CO"); //Colombia (Republic of)
        result.put(734, "VE"); //Venezuela (Bolivarian Republic of)
        result.put(736, "BO"); //Bolivia (Republic of)
        result.put(738, "GY"); //Guyana
        result.put(740, "EC"); //Ecuador
        result.put(742, "GF"); //French Guiana (French Department of)
        result.put(744, "PY"); //Paraguay (Republic of)
        result.put(746, "SR"); //Suriname (Republic of)
        result.put(748, "UY"); //Uruguay (Eastern Republic of)
        result.put(750, "FK"); //Falkland Islands (Malvinas)
        return Collections.unmodifiableMap(result);
    }

}
