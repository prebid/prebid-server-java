package org.prebid.server.functional.model.deals.lineitem.targeting

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.model.deals.lineitem.LineItemSize

import static BooleanOperator.AND
import static MatchingFunction.INTERSECTS
import static TargetingType.AD_UNIT_MEDIA_TYPE
import static TargetingType.AD_UNIT_SIZE
import static org.prebid.server.functional.model.deals.lineitem.MediaType.BANNER
import static org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator.NOT
import static org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator.OR

class Targeting {

    private final Map<BooleanOperator, List<TargetingNode>> rootNode

    private final Map<BooleanOperator, TargetingNode> singleTargetingRootNode

    @JsonValue
    def getSerializableRootNode() {
        rootNode ?: singleTargetingRootNode
    }

    private Targeting(Builder builder) {
        rootNode = [(builder.rootOperator): builder.targetingNodes]
    }

    private Targeting(Builder builder, TargetingNode targetingNode) {
        singleTargetingRootNode = [(builder.rootOperator): targetingNode]
    }

    Map<BooleanOperator, List<TargetingNode>> getTargetingRootNode() {
        rootNode.asImmutable()
    }

    static Targeting getDefaultTargeting() {
        defaultTargetingBuilder.build()
    }

    static Builder getDefaultTargetingBuilder() {
        new Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                     .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [BANNER])
    }

    static Targeting getInvalidTwoRootNodesTargeting() {
        defaultTargeting.tap { rootNode.put(OR, []) }
    }

    static class Builder {

        private BooleanOperator rootOperator
        private List<TargetingNode> targetingNodes = []

        Builder(BooleanOperator rootOperator = AND) {
            this.rootOperator = rootOperator
        }

        Builder addTargeting(TargetingType targetingType,
                             MatchingFunction matchingFunction,
                             List<?> targetingValues) {
            MatchingFunctionNode matchingFunctionNode = new MatchingFunctionNode(matchingFunctionMultipleValuesNode: [(matchingFunction): targetingValues])
            addTargetingNode(targetingType, matchingFunctionNode)
            this
        }

        Builder addTargeting(TargetingType targetingType,
                             MatchingFunction matchingFunction,
                             Object targetingValue) {
            MatchingFunctionNode matchingFunctionNode = new MatchingFunctionNode(matchingFunctionSingleValueNode: [(matchingFunction): targetingValue])
            addTargetingNode(targetingType, matchingFunctionNode)
            this
        }

        private void addTargetingNode(TargetingType targetingType,
                                      MatchingFunctionNode matchingFunctionNode) {
            targetingNodes << new TargetingNode([(targetingType): matchingFunctionNode])
        }

        Targeting build() {
            new Targeting(this)
        }

        Targeting buildNotBooleanOperatorTargeting(TargetingType targetingType,
                                                   MatchingFunction matchingFunction,
                                                   List<?> targetingValues) {
            rootOperator = NOT
            MatchingFunctionNode matchingFunctionNode = new MatchingFunctionNode(matchingFunctionSingleValueNode: [(matchingFunction): targetingValues])
            new Targeting(this, new TargetingNode([(targetingType): matchingFunctionNode]))
        }
    }
}
