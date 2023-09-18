package org.prebid.server.deals.targeting.syntax;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BooleanOperatorTest {

    @Test
    public void isBooleanOperatorShouldReturnTrueForKnownFunctions() {
        SoftAssertions.assertSoftly(softly -> {
            for (final String functionString : asList("$and", "$or", "$not")) {
                softly.assertThat(BooleanOperator.isBooleanOperator(functionString)).isTrue();
            }
        });
    }

    @Test
    public void isBooleanOperatorShouldReturnFalseForUnknownFunctions() {
        assertThat(BooleanOperator.isBooleanOperator("unknown")).isFalse();
    }

    @Test
    public void fromStringShouldReturnEnumValue() {
        // given
        final EnumMap<BooleanOperator, String> enumToString = new EnumMap<>(BooleanOperator.class);
        enumToString.put(BooleanOperator.AND, "$and");
        enumToString.put(BooleanOperator.OR, "$or");
        enumToString.put(BooleanOperator.NOT, "$not");

        // when and then
        SoftAssertions.assertSoftly(softly -> {
            for (final Map.Entry<BooleanOperator, String> entry : enumToString.entrySet()) {
                softly.assertThat(BooleanOperator.fromString(entry.getValue())).isEqualTo(entry.getKey());
            }
        });
    }

    @Test
    public void fromStringShouldThrowExceptionWhenUnkownFunction() {
        assertThatThrownBy(() -> BooleanOperator.fromString("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unrecognized boolean operator: unknown");
    }
}
