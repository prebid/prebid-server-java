package org.prebid.server.deals.lineitem;

import org.junit.Test;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.Token;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class DeliveryPlanTest {

    @Test
    public void getHighestUnspentTokensClassShouldReturnHighestUnspentToken() {
        // given
        final Set<Token> tokens = new HashSet<>();
        tokens.add(Token.of(1, 100));
        tokens.add(Token.of(2, 100));
        tokens.add(Token.of(3, 100));

        final DeliveryPlan plan = DeliveryPlan.of(DeliverySchedule.builder().tokens(tokens).build());

        IntStream.range(0, 150).forEach(i -> plan.incSpentToken());

        // when and then
        assertThat(plan.getHighestUnspentTokensClass()).isEqualTo(2);
    }
}
