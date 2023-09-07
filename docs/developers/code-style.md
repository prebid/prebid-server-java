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

### Immutability in DTO classes

Make data transfer object(DTO) classes immutable with static constructor. 
This can be achieved by using Lombok and `@Value(staticConstructor="of")`. When constructor uses multiple(more than 4) arguments, use builder instead(`@Builder`).
If dto must be modified somewhere, use builders annotation `toBuilder=true` parameter and rebuild instance by calling `toBuilder()` method.

```
// bad
public class MyDto {

    private final String value;
    
    public MyDto(String value) {
        this.value = value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}

// and later usage
final MyDto myDto = new MyDto("value");
myDto.setValue("newValue");

// good
@Builder(toBuilder=true)
@Value(staticConstructor="of")
public class MyDto {
    
    String value;
}

// and later usage
final MyDto myDto = MyDto.of("value");
final MyDto updatedDto = myDto.toBuilder().value("newValue").build();
```

### Variables types

Although Java supports the `var` keyword at the time of writing this documentation, the maintainers have chosen not to utilize it within the PBS codebase.
Instead, write full variable type.

```
// bad
final var result = getResult();

// good
final Data result = getResult(); 
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

This also applies for nested expressions.

```
// bad 
methodCall(
    nestedCall(
        long list of arguments
    )
);

// good
methodCall(
    nestedCall(
        long list of arguments));
```

### Method placement

Please, place methods inside a class in call order.

```
// bad
public interface Test {

    void a();
    
    void b();
}

public class TestImpl implements Test {

    @Override
    public void a() {
        c();
    }
    
    @Override
    public void b() {
        d();
    }
   
    private void d() {
    ...
    }
     
    private void c() {
    ...
    }
}

// good 
public interface Test {

    void a();
    
    void b();
}

public class TestImpl implements Test {

    @Override
    public void a() {
        c();
    }
    
    private void c() {
    ...
    }
    
    @Override
    public void b() {
        d();
    }
   
    private void d() {
    ...
    }
}

Explanation of an example: 
Define interface first method, then all methods that it is calling, then second method of an interface and all methods that it is calling, and so on.
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
return Collections.emptyList();
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

### Complex boolean logic

Do not rely on operator precedence in boolean logic, use parenthesis instead. This will make code simpler and less error-prone.

```
// bad
final boolean result = a && b || c;

// good
final boolean result = (a && b) || c;
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

### Optional usages

We are trying to get rid of long chains of null checks, which are described in suggestion above, in favor of Java `Optional` usage.

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

Try to write new bidders in the same manner with existing adapters. Utilize [sample bidder](https://docs.prebid.org/prebid-server/developers/add-new-bidder-java.html#adapter-code) code or use `GenericBidder` as a reference.</br></br>
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

#### Testing instance naming

The team decided to use name `target` for class instance under test.

#### Tests granularity

Unit tests should be as granular as possible. Try to split unit tests into smaller ones until this is impossible to do.

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

This also applies to cases where same method is tested with different arguments inside single unit test.
Note: This represents the replacement we have selected for parameterized testing.

```
// bad
@Test
public void testFooFirstSecond() {
    // when
    final String foo1 = service.getFoo(1);
    final String foo2 = service.getFoo(2);
    
    // then
    assertThat(foo1).isEqualTo("foo1");
    assertThat(foo2).isEqualTo("foo2");
}

// good
@Test
public void testFooFirst() {
    // when
    final String foo1 = service.getFoo(1);
    
    // then
    assertThat(foo1).isEqualTo("foo1");
}

@Test
public void testFooSecond() {
    // when
    final String foo2 = service.getFoo(2);
    
    // then
    assertThat(foo2).isEqualTo("foo2");
}
```

#### Unit tests naming

Name unit tests meaningfully. Test names should give brief description of what unit test tries to check.
It is also recommended to structure test method names with this scheme:
name of method that is being tested, word `should`, what a method should return.
If a method should return something based on a certain condition, add word `when` and description of a condition.

```
// bad
@Test
public void doSomethingTest() {
    // when and then
    assertThat(service.processData("data")).isEqualTo("result");
}

// good
@Test
public void processDataShouldReturnResultWhenInputIsData() {
    // when and then
    assertThat(service.processData("data")).isEqualTo("result");
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

This point also implies the next one.

#### Avoid class level constants in test classes

Since we are trying to improve test simplicity and readability and place test data close to tests, we decided to avoid usage of top level constants where it is possible.
Instead, just inline constant values.

```
// bad
public class TestClass {

    private static final String CONSTANT_1 = "foo";
    ...
    private static final String CONSTANT_N = "bar";
    
    // A bunch of other tests
    
    @Test
    public void testFoo() {
        // when and then
        assertThat(service.foo(CONSTANT_1)).isEqualTo(CONSTANT_N);
    }
}

// good
public class TestClass {
    // A bunch of other tests
    
    @Test
    public void testFoo() {
        // when and then
        assertThat(service.foo("foo")).isEqualTo("bar");
    }
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
