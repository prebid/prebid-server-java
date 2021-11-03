package org.prebid.server.functional.util

import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils

import java.text.DecimalFormat
import java.util.stream.IntStream

import static java.lang.Integer.MAX_VALUE
import static java.lang.Integer.MIN_VALUE
import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION

class PBSUtils {

    static int getRandomNumber(int min = 0, int max = MAX_VALUE) {
        new Random().nextInt(max - min) + min
    }

    static int getRandomNegativeNumber(int min = MIN_VALUE + 1, int max = 0) {
        getRandomNumber(min, max)
    }

    static float getFractionalRandomNumber(int min = 0, int max = MAX_VALUE) {
        new Random().nextFloat() * (max - min) + min
    }

    static float getRoundFractionalNumber(float number, int numberDecimalPlaces) {
        def stringBuilder = new StringBuilder().append("##.")
        IntStream.range(0, numberDecimalPlaces).forEach { index -> stringBuilder.append("#") }
        def format = new DecimalFormat(stringBuilder.toString())
        Float.valueOf(format.format(number))
    }

    static String getRandomString(int stringLength = 20) {
        RandomStringUtils.randomAlphanumeric(stringLength)
    }

    static String getPbsVersion() {
        PBS_VERSION.split("-")[0]
    }
}
