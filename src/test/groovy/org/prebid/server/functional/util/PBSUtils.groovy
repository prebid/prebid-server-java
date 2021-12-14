package org.prebid.server.functional.util

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.testcontainers.Dependencies
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils

import java.nio.file.Files
import java.nio.file.Path
import java.text.DecimalFormat
import java.util.stream.IntStream

import static java.lang.Integer.MAX_VALUE
import static java.lang.Integer.MIN_VALUE

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

    static float getRoundedFractionalNumber(float number, int numberDecimalPlaces) {
        def stringBuilder = new StringBuilder().append("##.")
        IntStream.range(0, numberDecimalPlaces).forEach { index -> stringBuilder.append("#") }
        def format = new DecimalFormat(stringBuilder.toString())
        Float.valueOf(format.format(number))
    }

    static String getRandomString(int stringLength = 20) {
        RandomStringUtils.randomAlphanumeric(stringLength)
    }

    static Path createJsonFile(BidRequest bidRequest) {
        def data = Dependencies.objectMapperWrapper.encode(bidRequest)
        createTempFile(data, ".json")
    }

    private static Path createTempFile(String content, String suffix) {
        def path = Files.createTempFile(null, suffix)
        path.toFile().tap {
            deleteOnExit()
            it << content
        }
        path
    }
}
