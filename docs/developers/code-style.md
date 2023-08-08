# Code Style

The intent here is to maintain a common style across the project and rely on the process to enforce it instead of
individuals.

## Automated code style check

The [pom.xml](../../pom.xml) is configured to enforce a coding style defined in [checkstyle.xml](../../checkstyle.xml) when
maven `validate` phase executed.

## Formatting

The project uses formatting rules described in `.editorconfig` file. Most of the popular IDEs have support of it. For
example, in `IntelliJ IDEA` hit `Code -> Reformat Code` to organize your code.

## Code conventions

### Max line length

Line length is limited to 120 columns for `*.java` files. Other file types are unrestricted.

### Transitive dependencies

Don't use transitive dependencies in project code. If it needed, recommended adding a library as a dependency of Maven
in `pom.xml` directly.

### Add library as maven dependency

It is recommended to define version of library to separate property in `pom.xml`:

```
<project>
    <properties>
        <caffeine.version>2.6.2</caffeine.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
            <version>${caffeine.version}</version>
        </dependency>
    </dependencies>
</project>
```

### Avoid wildcards in imports

Do not use wildcard in imports because they hide what exactly is required by the class.

```
// bad
import java.util.*;

// good
import java.util.HashMap;
import java.util.Map;
```

### Variable and method naming

Prefer to use `camelCase` naming convention for variables and methods.

```
// bad
String account_id = "id";

// good
String accountId = "id";
```

Name of variable should be self-explanatory:

```
// bad
String s = resolveParamA();

// good
String resolvedParamA = resolveParamA();
```

This helps other developers flesh your code out better without additional questions.

For `Map`s it is recommended to use `To` between key and value designation:

```
// bad
Map<Imp, ExtImp> map = getData();

// good
Map<Imp, ExtImp> impToExt = getData();
```
### Parenthesis placement

Enclosing parenthesis should be placed on expression end.

```
// bad
methodCall(
    long list of arguments
);

// good
methodCall(
    long list of arguments);
```

### Separation of method signature definition and body

Not strict, but methods with long parameters list, that cannot be placed on single line,
should add empty line before body definition.

```
// bad
public static void method(
    parameters definitions) {
    start of body definition
    
// good
public static void method(
    parameters definitions) {
    
    start of body definition
```

### Use special methods for short collections initializations

Use collection literals where it is possible to define and initialize collections.

```
// bad 
final List<String> foo = new ArrayList();
foo.add("bar");

// good 
final List<String> foo = List.of("bar");
```

Also, use special methods of Collections class for one - line empty collection creation. This makes developer intention clear and code less error prone.

```
// bad 
return List.of();

// good
return Collections.singletonList();
```


### Make variables final

It is recommended to declare variable as `final`- not strict but rather project convention to keep the code safe.

```
// bad
String value = "value";

// good
final String value = "value";
```

### Ternary expressions

Results of long ternary operators should be on separate lines:

```
// bad
boolean result = someVeryVeryLongConditionThatForcesLineWrap ? firstResult
    : secondResult;

// good
boolean result = someVeryVeryLongConditionThatForcesLineWrap
    ? firstResult
    : secondResult;
```

Not so strict, but short ternary operations should be on one line:

```
// bad
boolean result = someShortCondition
    ? firstResult
    : secondResult;

// good
boolean result = someShortCondition ? firstResult : secondResult;
```

### Nested method calls

Try to avoid hard-readable multiple nested method calls:

```
// bad
int resolvedValue = resolveValue(fetchExternalJson(url, httpClient), populateAdditionalKeys(mainKeys, keyResolver));

// good
String externalJson = fetchExternalJson(url, httpClient);
List<Key> additionalKeys = fetchAdditionalKeys(mainKeys, keyResolver);
int resolvedValue = resolveValue(externalJson, additionalKeys);
```

### Data retrieval calls of same result

Try not to retrieve same data more than once:

```
// bad
if (getData() != null) {
    final Data resolvedData = resolveData(getData());
    ...
}

// good
final Data data = getData();
if (data != null) {
    final Data resolvedData = resolveData(data);
    ...
}
```

### Check for NULL

If you're dealing with incoming data, please be sure to check if the nested object is not null before chaining.

```
// bad
final ExtRequestTargeting targeting = bidRequest.getExt().getPrebid().getTargeting();

// good
final ExtRequest requestExt = bidRequest.getExt();
final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
```

For convenience, the `org.prebid.server.util.ObjectUtil` helper can be used for such kind of operations.

### Garbage code

Don't leave commented code (don't think about the future).

```
// bad
// String iWillUseThisLater = "never";
```

You can always add it later when it will be really desired.

### Privacy

It is strictly prohibited to log any kind of private data about publisher, exchanges or similar sensitive information.
The idea is to keep this open-source project safe as far as possible.

### Bidder implementation

Try to write new bidders in the same manner with existing adapters. Utilize sample bidder code or use `GenericBidder` as a reference.</br></br>
This is needed because bidder adapters tend to be modified frequently. In world where each bidder is written using different coding styles and techniques, maintainers would need to spend long time to understand bidders code before adding any modifications.
On the other hand, if each bidder adapter is written using common constructs, it is easy to review and modify bidders fast.

### Tests

The code should be covered over 90%.

#### Given-When-Then approach

The common way for writing tests has to comply with `given-when-then` style.

```
// given
final BidRequest bidRequest = BidRequest.builder().id("").build();

// when
final ValidationResult result = requestValidator.validate(bidRequest);

// then
assertThat(result.getErrors()).containsOnly("request missing required field: \"id\"");
```

where:

- `given` - initial state, data or conditions.
- `when` - stimulus: some action against the system under test.
- `then` - expectations/assertions.

#### Tests granularity

Unit tests should be as granular as possible.

```
// bad
@Test
public void testFooBar() {
    // when
    final String foo = service.getFoo();
    final String bar = service.getBar();
    
    // then
    assertThat(foo).isEqualTo("foo");
    assertThat(bar).isEqualTo("bar");
}

// good
@Test
public void testFoo() {
    // when
    final String foo = service.getFoo();
    
    // then
    assertThat(foo).isEqualTo("foo");
}

@Test
public void testBar() {
    // when
    final String bar = service.getBar();
    
    // then
    assertThat(bar).isEqualTo("bar");
}
```
#### Unit tests naming

Name unit tests meaningfully. Test names should give brief description of what unit test tries to check.
It is also recommended to prefix test methods with the name of method that is being tested and word `should`.

```
// bad
@Test
public void doSomethingTest() {
    // when and then
    assertThat(service.processData()).isEqualTo("result");
}

// good
@Test
public void processDataShouldReturnResult() {
    // when and then
    assertThat(service.processData()).isEqualTo("result");
}
```

#### Place test data as close as possible to test

Place data used in test as close as possible to test code. This will make tests easier to read, review and understand.

```
// bad
@Test
public void testFoo() {
    // given
    final String fooData = getSpecificFooData();
    
    // when and then
    assertThat(service.processFoo(fooData)).isEqualTo(getSpecificFooResult());
}

// good
@Test
public void testFoo() {
    // given
    final String fooData = "fooData";
    
    // when and then
    assertThat(service.processFoo(fooData)).isEqualTo("fooResult");
}
```

#### Real data in tests

Don't use real information in tests, like existing endpoint URLs, account IDs, etc.

```
// bad
String ENDPOINT_URL = "https://prebid.org";

// good
String ENDPOINT_URL = "https://test-endpoint.url";
```

#### Bidder smoke tests

Along with regular unit-tests bidder's writer should provide smoke (historically we call them `integration`) tests.
Those tests are located at `src/test/java/org/prebid/server/it` folder.

The idea behind the smoke bidder test is to verify PBS can start up with supplied bidder configuration and to check the
simplest basic happy-path scenario which bidder code should do. Thus, the OpenRTB `JSON` request file (see the examples
in `src/test/resources/org/prebid/server/it/openrtb2` folder)might contain exactly single bidder under testing and one
impression with single media type.

```json
{
    "id": "request_id",
    "imp": [
        {
            "id": "imp_id",
            "banner": {
                "w": 320,
                "h": 250
            },
            "ext": {
                "bidder_name": {
                    "param1": "value1"
                }
            }
        }
    ],
    "tmax": 5000,
    "regs": {
        "ext": {
            "gdpr": 0
        }
    }
}
```

All possible scenarios for testing functionality must be covered by bidder's unit-tests.
