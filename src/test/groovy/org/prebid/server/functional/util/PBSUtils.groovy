package org.prebid.server.functional.util

import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.text.RandomStringGenerator
import org.prebid.server.functional.model.request.auction.BidRequest

import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path

import static java.lang.Integer.MAX_VALUE
import static java.lang.Integer.MIN_VALUE
import static java.math.RoundingMode.HALF_UP
import static org.prebid.server.functional.tests.pricefloors.PriceFloorsBaseSpec.FLOOR_MAX
import static org.prebid.server.functional.tests.pricefloors.PriceFloorsBaseSpec.FLOOR_MIN
import static org.prebid.server.functional.util.SystemProperties.DEFAULT_TIMEOUT

class PBSUtils implements ObjectMapperWrapper {

    private static final int DEFAULT_NUMBER_PRECISION = 6

    static int getRandomNumber(int min = 0, int max = MAX_VALUE) {
        int upperBound = max == MAX_VALUE ? max : max + 1
        new Random().nextInt(upperBound - min) + min
    }

    static int getRandomNegativeNumber(int min = MIN_VALUE + 1, int max = 0) {
        getRandomNumber(max, min * -1) * - 1
    }

    static BigDecimal getRandomDecimal(float min = 0, float max = MAX_VALUE) {
        def number = new Random().nextFloat() * (max - min) + min
        roundDecimal(BigDecimal.valueOf(number), DEFAULT_NUMBER_PRECISION)
    }

    static BigDecimal roundDecimal(BigDecimal number, int decimalPlaces) {
        number.setScale(decimalPlaces, RoundingMode.HALF_EVEN)
    }

    static String getRandomString(int stringLength = 20) {
        RandomStringUtils.randomAlphanumeric(stringLength)
    }

    static <T> T getRandomElement(List<T> list) {
        list[getRandomNumber(0, list.size() - 1)]
    }

    static BigDecimal getRandomFloorValue(float floorMin = FLOOR_MIN, float floorMax = FLOOR_MAX) {
        roundDecimal(getRandomDecimal(floorMin, floorMax), 2)
    }

    static def randomizeCase(String string) {
        string.toCharArray().collect {
            def number = getRandomNumber(0, 1)
            if (number == 0) {
                it.toLowerCase()
            } else {
                it.toUpperCase()
            }
        }.join()
    }

    static Path createJsonFile(BidRequest bidRequest) {
        def data = encode(bidRequest)
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

    static void waitUntil(Closure closure, long timeoutMs = DEFAULT_TIMEOUT, long pollInterval = 100) {
        def isConditionFulfilled = false
        def waiterElapsedTime = 0
        def waiterStartTime = System.currentTimeMillis()

        while (waiterElapsedTime <= timeoutMs) {
            if (closure()) {
                isConditionFulfilled = true
                break
            } else {
                waiterElapsedTime = System.currentTimeMillis() - waiterStartTime
                sleep(pollInterval)
            }
        }

        if (!isConditionFulfilled) {
            throw new IllegalStateException("Condition was not fulfilled within $timeoutMs ms.")
        }
    }

    static BigDecimal getRandomPrice(int min = 0, int max = 10, int scale = 3) {
        getRandomDecimal(min, max).setScale(scale, HALF_UP)
    }
}
