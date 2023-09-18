package org.prebid.server.deals.targeting.syntax;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MatchingFunctionTest {

    @Test
    public void isMatchingFunctionShouldReturnTrueForKnownFunctions() {
        SoftAssertions.assertSoftly(softly -> {
            for (final String functionString : asList("$matches", "$in", "$intersects", "$within")) {
                softly.assertThat(MatchingFunction.isMatchingFunction(functionString)).isTrue();
            }
        });
    }

    @Test
    public void isMatchingFunctionShouldReturnFalseForUnknownFunctions() {
        assertThat(MatchingFunction.isMatchingFunction("unknown")).isFalse();
    }

    @Test
    public void fromStringShouldReturnEnumValue() {
        // given
        final EnumMap<MatchingFunction, String> enumToString = new EnumMap<>(MatchingFunction.class);
        enumToString.put(MatchingFunction.MATCHES, "$matches");
        enumToString.put(MatchingFunction.IN, "$in");
        enumToString.put(MatchingFunction.INTERSECTS, "$intersects");
        enumToString.put(MatchingFunction.WITHIN, "$within");

        // when and then
        SoftAssertions.assertSoftly(softly -> {
            for (final Map.Entry<MatchingFunction, String> entry : enumToString.entrySet()) {
                softly.assertThat(MatchingFunction.fromString(entry.getValue())).isEqualTo(entry.getKey());
            }
        });
    }

    @Test
    public void fromStringShouldThrowExceptionWhenUnkownFunction() {
        assertThatThrownBy(() -> MatchingFunction.fromString("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unrecognized matching function: unknown");
    }
}
