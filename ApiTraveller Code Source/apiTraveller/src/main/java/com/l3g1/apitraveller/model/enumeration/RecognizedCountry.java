package com.l3g1.apitraveller.model.enumeration;


//Enumeration of countries accepted for the attribute "localization".
public enum RecognizedCountry {
    AFGHANISTAN,
    ALAND_ISLANDS,
    ALBANIA,
    ALGERIA,
    AMERICAN_SAMOA,
    ANDORRA,
    ANGOLA,
    ANGUILLA,
    ANTARCTICA,
    ANTIGUA_AND_BARBUDA,
    ARGENTINA,
    ARMENIA,
    ARUBA,
    AUSTRALIA,
    AUSTRIA,
    AZERBAIJAN,
    THE_BAHAMAS,
    BAHRAIN,
    BANGLADESH,
    BARBADOS,
    BELARUS,
    BELGIUM,
    BELIZE,
    BENIN,
    BERMUDA,
    BHUTAN,
    BOLIVIA,
    BOSNIA_AND_HERZEGOVINA,
    BOTSWANA,
    BOUVET_ISLAND,
    BRAZIL,
    BRITISH_INDIAN_OCEAN_TERRITORY,
    BRUNEI,
    BULGARIA,
    BURKINA_FASO,
    BURUNDI,
    CAMBODIA,
    CAMEROON,
    CANADA,
    CAPE_VERDE,
    CAYMAN_ISLANDS,
    CENTRAL_AFRICAN_REPUBLIC,
    CHAD,
    CHILE,
    CHINA,
    CHRISTMAS_ISLAND,
    COCOS_KEELING_ISLANDS,
    COLOMBIA,
    COMOROS,
    CONGO,
    DEMOCRATIC_REPUBLIC_OF_THE_CONGO,
    COOK_ISLANDS,
    COSTA_RICA,
    IVORY_COAST,
    CROATIA,
    CUBA,
    CYPRUS,
    CZECH_REPUBLIC,
    DENMARK,
    DJIBOUTI,
    DOMINICA,
    DOMINICAN_REPUBLIC,
    EAST_TIMOR,
    ECUADOR,
    EGYPT,
    EL_SALVADOR,
    EQUATORIAL_GUINEA,
    ERITREA,
    ESTONIA,
    ETHIOPIA,
    FALKLAND_ISLANDS,
    FAROE_ISLANDS,
    FIJI_ISLANDS,
    FINLAND,
    FRANCE,
    FRENCH_GUIANA,
    FRENCH_POLYNESIA,
    FRENCH_SOUTHERN_TERRITORIES,
    GABON,
    THE_GAMBIA,
    GEORGIA,
    GERMANY,
    GHANA,
    GIBRALTAR,
    GREECE,
    GREENLAND,
    GRENADA,
    GUADELOUPE,
    GUAM,
    GUATEMALA,
    GUERNSEY_AND_ALDERNEY,
    GUINEA,
    GUINEA_BISSAU,
    GUYANA,
    HAITI,
    HEARD_ISLAND_AND_MCDONALD_ISLANDS,
    HONDURAS,
    HONG_KONG,
    HUNGARY,
    ICELAND,
    INDIA,
    INDONESIA,
    IRAN,
    IRAQ,
    IRELAND,
    ISRAEL,
    ITALY,
    JAMAICA,
    JAPAN,
    JERSEY,
    JORDAN,
    KAZAKHSTAN,
    KENYA,
    KIRIBATI,
    NORTH_KOREA,
    SOUTH_KOREA,
    KUWAIT,
    KYRGYZSTAN,
    LAOS,
    LATVIA,
    LEBANON,
    LESOTHO,
    LIBERIA,
    LIBYA,
    LIECHTENSTEIN,
    LITHUANIA,
    LUXEMBOURG,
    MACAU,
    MACEDONIA,
    MADAGASCAR,
    MALAWI,
    MALAYSIA,
    MALDIVES,
    MALI,
    MALTA,
    ISLE_OF_MAN,
    MARSHALL_ISLANDS,
    MARTINIQUE,
    MAURITANIA,
    MAURITIUS,
    MAYOTTE,
    MEXICO,
    MICRONESIA,
    MOLDOVA,
    MONACO,
    MONGOLIA,
    MONTENEGRO,
    MONTSERRAT,
    MOROCCO,
    MOZAMBIQUE,
    MYANMAR,
    NAMIBIA,
    NAURU,
    NEPAL,
    BONAIRE,
    NETHERLANDS,
    NEW_CALEDONIA,
    NEW_ZEALAND,
    NICARAGUA,
    NIGER,
    NIGERIA,
    NIUE,
    NORFOLK_ISLAND,
    NORTHERN_MARIANA_ISLANDS,
    NORWAY,
    OMAN,
    PAKISTAN,
    PALAU,
    PALESTINE,
    PANAMA,
    PAPUA_NEW_GUINEA,
    PARAGUAY,
    PERU,
    PHILIPPINES,
    PITCAIRN_ISLAND,
    POLAND,
    PORTUGAL,
    PUERTO_RICO,
    QATAR,
    REUNION,
    ROMANIA,
    RUSSIA,
    RWANDA,
    SAINT_HELENA,
    SAINT_KITTS_AND_NEVIS,
    SAINT_LUCIA,
    SAINT_PIERRE_AND_MIQUELON,
    SAINT_VINCENT_AND_THE_GRENADINES,
    SAINT_BARTHELEMY,
    SAINT_MARTIN,
    SAMOA,
    SAN_MARINO,
    SAO_TOME_AND_PRINCIPE,
    SAUDI_ARABIA,
    SENEGAL,
    SERBIA,
    SEYCHELLES,
    SIERRA_LEONE,
    SINGAPORE,
    SLOVAKIA,
    SLOVENIA,
    SOLOMON_ISLANDS,
    SOMALIA,
    SOUTH_AFRICA,
    SOUTH_GEORGIA,
    SOUTH_SUDAN,
    SPAIN,
    SRI_LANKA,
    SUDAN,
    SURINAME,
    SVALBARD_AND_JAN_MAYEN_ISLANDS,
    SWAZILAND,
    SWEDEN,
    SWITZERLAND,
    SYRIA,
    TAIWAN,
    TAJIKISTAN,
    TANZANIA,
    THAILAND,
    TOGO,
    TOKELAU,
    TONGA,
    TRINIDAD_AND_TOBAGO,
    TUNISIA,
    TURKEY,
    TURKMENISTAN,
    TURKS_AND_CAICOS_ISLANDS,
    TUVALU,
    UGANDA,
    UKRAINE,
    UNITED_ARAB_EMIRATES,
    UNITED_KINGDOM,
    UNITED_STATES,
    UNITED_STATES_MINOR_OUTLYING_ISLANDS,
    URUGUAY,
    UZBEKISTAN,
    VANUATU,
    VATICAN_CITY,
    VENEZUELA,
    VIETNAM,
    VIRGIN_ISLANDS_BRITISH,
    VIRGIN_ISLANDS_US,
    WALLIS_AND_FUTUNA_ISLANDS,
    WESTERN_SAHARA,
    YEMEN,
    ZAMBIA,
    ZIMBABWE,
    KOSOVO,
    CURACAO,
    SINT_MAARTEN;

    //Checks if the provided value is a valid recognized country for the attribute "localization".
    public static boolean isValidValue(String valeur) {
        for (RecognizedCountry recognizedCountry : RecognizedCountry.values()) {
            if (recognizedCountry.name().equalsIgnoreCase(valeur)) {
                return true;
            }
        }
        return false;
    }
}
