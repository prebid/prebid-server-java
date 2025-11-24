package org.prebid.server.functional.util

import org.apache.commons.lang3.RandomStringUtils
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

    static int getRandomBinary() {
        return new Random().nextInt(2)
    }

    static int getRandomNumberWithExclusion(int excludedValue, int min = 0, int max = MAX_VALUE) {
        def value = getRandomNumber(min, max)
        value == excludedValue ? getRandomNumberWithExclusion(excludedValue, min, max) : value
    }

    static int getRandomNumberWithExclusion(List<Integer> excludedValues, int min = 0, int max = MAX_VALUE) {
        def value = getRandomNumber(min, max)
        excludedValues.contains(value) ? getRandomNumberWithExclusion(excludedValues, min, max) : value
    }

    static int getRandomNegativeNumber(int min = MIN_VALUE + 1, int max = 0) {
        getRandomNumber(max, min * -1) * -1
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

    static String getRandomSpecialChars(int stringLength = 20) {
        RandomStringUtils.random(stringLength, "!@#\$%^&*()-_=+[]{}|;:'\",.<>/?")
    }

    static String getRandomStringWithSpecials(int stringLength = 20) {
        RandomStringUtils.randomAscii(stringLength)
    }

    static Boolean getRandomBoolean() {
        new Random().nextBoolean()
    }

    static <T> T getRandomElement(List<T> list) {
        list[getRandomNumber(0, list.size() - 1)]
    }

    static BigDecimal getRandomFloorValue(float floorMin = FLOOR_MIN, float floorMax = FLOOR_MAX) {
        roundDecimal(getRandomDecimal(floorMin, floorMax), 2)
    }

    static def getRandomCase(String string) {
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

    static BigDecimal getRandomPrice(int min = 1, int max = 10, int scale = 3) {
        getRandomDecimal(min, max).setScale(scale, HALF_UP)
    }

    static <T extends Enum<T>> T getRandomEnum(Class<T> anEnum, List<T> exclude = []) {
        def values = anEnum.enumConstants.findAll { !exclude.contains(it) } as T[]
        values[getRandomNumber(0, values.size() - 1)]
    }

    static String convertCase(String input, Case caseType) {
        def words = input.replaceAll(/([a-z])([A-Z])/) { match, p1, p2 -> "${p1}_${p2.toLowerCase()}" }
                .split(/[_\-\s]+|\B(?=[A-Z])/).collect { it.toLowerCase() }

        switch (caseType) {
            case Case.KEBAB:
                return words.join('-')
            case Case.SNAKE:
                return words.join('_')
            case Case.CAMEL:
                def camelCase = words.head() + words.tail().collect { it.capitalize() }.join('')
                return camelCase
            default:
                throw new IllegalArgumentException("Unknown case type: $caseType")
        }
    }

    static String getRandomVersion(String minVersion = "0.0.0", String maxVersion = "99.99.99") {
        def minParts = minVersion.split('\\.').collect { it.toInteger() }
        def maxParts = maxVersion.split('\\.').collect { it.toInteger() }
        def versionParts = []

        def major = getRandomNumber(minParts[0], maxParts[0])
        versionParts << major

        def minorMin = (major == minParts[0]) ? minParts[1] : 0
        def minorMax = (major == maxParts[0]) ? maxParts[1] : 99
        def minor = getRandomNumber(minorMin, minorMax)
        versionParts << minor

        if (minParts.size() > 2 || maxParts.size() > 2) {
            def patchMin = (major == minParts[0] && minor == minParts[1]) ? minParts[2] : 0
            def patchMax = (major == maxParts[0] && minor == maxParts[1]) ? maxParts[2] : 99
            def patch = getRandomNumber(patchMin, patchMax)
            versionParts << patch
        }
        def version = versionParts.join('.')
        return (version >= minVersion && version <= maxVersion) ? version : getRandomVersion(minVersion, maxVersion)
    }

    static Boolean isUUID(String str) {
        if (str == null) {
            return false
        }
        try {
            UUID.fromString(str)
            return true
        } catch (IllegalArgumentException e) {
            return false
        }
    }

    static Boolean isApproximatelyEqual(Integer actual, Integer expected, Integer tolerance = 100) {
        Math.abs(actual - expected) <= tolerance
    }
}
