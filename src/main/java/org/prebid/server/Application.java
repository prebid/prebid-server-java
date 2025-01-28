package org.prebid.server;

import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.execution.ruleengine.Mutation;
import org.prebid.server.execution.ruleengine.MutationFactory;
import org.prebid.server.execution.ruleengine.extractors.ArgumentExtractor;
import org.prebid.server.execution.ruleengine.extractors.BooleanExtractor;
import org.prebid.server.execution.ruleengine.extractors.IntegerExtractor;
import org.prebid.server.execution.ruleengine.extractors.StringExtractor;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Map;

@SuppressWarnings("checkstyle:hideutilityclassconstructor")
@SpringBootApplication
public class Application {

    @Value
    @Builder(toBuilder = true)
    private static class Test {

        String name;

        int version;

        boolean test;
    }

    private static final String NAME_EXTRACTOR = "name-extractor";
    private static final String VERSION_EXTRACTOR = "version-extractor";
    private static final String IS_TEST_EXTRACTOR = "is-test-extractor";

    public static void main(String[] args) {
        final Map<String, ArgumentExtractor<Test, ?>> argumentExtractors =
                Map.of(NAME_EXTRACTOR, BooleanExtractor.of(test -> test.test),
                        VERSION_EXTRACTOR, IntegerExtractor.of(test -> test.version),
                        IS_TEST_EXTRACTOR, StringExtractor.of(test -> test.name));

        final MutationFactory<Test> mutationFactory = new MutationFactory<>(
                argumentExtractors,
                node -> test -> test.toBuilder().name(node.textValue()).build());

        final Mutation<Test> mutation = mutationFactory.parse(
                List.of(NAME_EXTRACTOR, NAME_EXTRACTOR, VERSION_EXTRACTOR, IS_TEST_EXTRACTOR),
                Map.of(
                        "true|true|123|hello", TextNode.valueOf("1"),
                        "true|true|5|test", TextNode.valueOf("2"),
                        "false|false|123|bruh", TextNode.valueOf("3"),
                        "true|false|1|hello", TextNode.valueOf("4"),
                        "true|true|-2|aloha", TextNode.valueOf("5"),
                        "true|false|-2|hello", TextNode.valueOf("6"),
                        "true|true|123|last", TextNode.valueOf("7")));

        final Test mutated = mutation.mutate(new Test("bruh", 123, false));
        System.out.println(mutated);
    }
}
