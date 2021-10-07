package org.prebid.server.deals.lineitem;

import org.prebid.server.deals.proto.Token;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

public class DeliveryToken implements Comparable<DeliveryToken> {

    private static final Comparator<DeliveryToken> COMPARATOR = Comparator.comparing(DeliveryToken::getPriorityClass);

    private final Token token;

    private final LongAdder spent;

    private DeliveryToken(DeliveryToken deliveryToken) {
        this(deliveryToken.token, new LongAdder());
    }

    private DeliveryToken(Token token) {
        this(token, new LongAdder());
    }

    private DeliveryToken(Token token, LongAdder spent) {
        this.token = Objects.requireNonNull(token);
        this.spent = Objects.requireNonNull(spent);
    }

    public static DeliveryToken of(DeliveryToken deliveryToken) {
        return new DeliveryToken(deliveryToken);
    }

    public static DeliveryToken of(Token token) {
        return new DeliveryToken(token);
    }

    /**
     * Return unspent tokens from {@link DeliveryToken}.
     */
    public int getUnspent() {
        return (int) (token.getTotal() - spent.sum());
    }

    public void inc() {
        spent.increment();
    }

    public DeliveryToken mergeWithToken(Token nextToken, boolean sumTotal) {
        if (nextToken == null) {
            return this;
        } else {
            final int total = sumTotal
                    ? getTotal() + nextToken.getTotal()
                    : nextToken.getTotal();
            return new DeliveryToken(Token.of(getPriorityClass(), total), spent);
        }
    }

    public LongAdder getSpent() {
        return spent;
    }

    public Integer getTotal() {
        return token.getTotal();
    }

    public Integer getPriorityClass() {
        return token.getPriorityClass();
    }

    @Override
    public int compareTo(DeliveryToken another) {
        return COMPARATOR.compare(this, another);
    }
}
