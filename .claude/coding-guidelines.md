# Browser API - Claude Coding Guidelines

## General Principles

- Write clean, maintainable, and testable code
- Favor explicitness over cleverness
- Keep methods small and focused (single responsibility)
- Use meaningful names that reveal intent
- Prefer composition over inheritance
- Write code that is easy to delete

## Modern Java Style (Java 21+)

### Use Records for Immutable Data

```java
// ✅ GOOD - Use records for DTOs and value objects
public record ExtractionRequest(
    String url,
    String selector,
    ExtractionType type
) {}

// ❌ BAD - Don't use verbose classes for simple data
public class ExtractionRequest {
    private String url;
    private String selector;
    // ... getters, setters, equals, hashCode, toString
}
```

### Pattern Matching & Switch Expressions

```java
// ✅ GOOD - Use modern switch expressions
String result = switch (extractionType) {
    case HTML -> htmlExtractor.extract(request);
    case CSS -> cssExtractor.extract(request);
    case JSON -> jsonExtractor.extract(request);
};

// ✅ GOOD - Pattern matching for instanceof
if (value instanceof String s && !s.isEmpty()) {
    return s.toUpperCase();
}
```

### Sealed Classes for Type Safety

```java
// ✅ GOOD - Use sealed classes for closed hierarchies
public sealed interface ExtractionResult
    permits SuccessResult, ErrorResult {}

public record SuccessResult(String data) implements ExtractionResult {}
public record ErrorResult(String message, Throwable cause) implements ExtractionResult {}
```

### Text Blocks for Multi-line Strings

```java
// ✅ GOOD - Use text blocks
String html = """
    <div class="component">
        <h1>%s</h1>
    </div>
    """.formatted(title);
```

## Functional Programming

### Use Streams for Collections

```java
// ✅ GOOD - Functional style with streams
List<String> activeUrls = sessions.stream()
    .filter(Session::isActive)
    .map(Session::getUrl)
    .distinct()
    .sorted()
    .toList();

// ❌ BAD - Imperative loops when streams are clearer
List<String> activeUrls = new ArrayList<>();
for (Session session : sessions) {
    if (session.isActive()) {
        String url = session.getUrl();
        if (!activeUrls.contains(url)) {
            activeUrls.add(url);
        }
    }
}
Collections.sort(activeUrls);
```

### Optional for Null Safety

```java
// ✅ GOOD - Use Optional for potentially absent values
public Optional<CachedResponse> findInCache(String key) {
    return Optional.ofNullable(cache.get(key));
}

// ✅ GOOD - Chain Optional operations
return findInCache(url)
    .filter(response -> !response.isExpired())
    .map(CachedResponse::getData)
    .orElseGet(() -> fetchFresh(url));

// ❌ BAD - Returning null
public CachedResponse findInCache(String key) {
    return cache.get(key); // might be null!
}
```

### Prefer Immutability

```java
// ✅ GOOD - Immutable by default
public record ComponentPackage(
    String html,
    String css,
    String javascript,
    List<String> assets
) {
    // Defensive copy for mutable fields
    public ComponentPackage {
        assets = List.copyOf(assets);
    }
}

// ✅ GOOD - Use final for local variables
final var result = processor.process(input);
final var transformed = result.transform();
```

## Spring Boot Best Practices

### Dependency Injection

```java
// ✅ GOOD - Constructor injection (preferred)
@Service
public class ExtractionService {
    private final BrowserManager browserManager;
    private final CacheService cacheService;

    public ExtractionService(BrowserManager browserManager, CacheService cacheService) {
        this.browserManager = browserManager;
        this.cacheService = cacheService;
    }
}

// ❌ BAD - Field injection
@Service
public class ExtractionService {
    @Autowired
    private BrowserManager browserManager; // harder to test
}
```

### REST Controllers

```java
// ✅ GOOD - Clear, semantic endpoints
@RestController
@RequestMapping("/api/v1/extract")
public class ExtractionController {

    @GetMapping
    public ResponseEntity<ExtractionResponse> extract(
        @RequestParam String url,
        @RequestParam String selector,
        @RequestParam(defaultValue = "HTML") ExtractionType type
    ) {
        // implementation
    }
}
```

## Error Handling

### Use Specific Exceptions

```java
// ✅ GOOD - Domain-specific exceptions
public class BrowserSessionException extends RuntimeException {
    public BrowserSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}

// ✅ GOOD - Handle at appropriate level
try {
    return browserManager.createSession(url);
} catch (PlaywrightException e) {
    throw new BrowserSessionException("Failed to create browser session for: " + url, e);
}
```

### Return Results, Not Exceptions

```java
// ✅ GOOD - Use sealed result types for expected failures
public sealed interface ExtractionResult permits Success, Failure {
    record Success(String data) implements ExtractionResult {}
    record Failure(String error) implements ExtractionResult {}
}

// ✅ GOOD - Caller handles both cases explicitly
ExtractionResult result = extractor.extract(url);
return switch (result) {
    case Success(var data) -> ResponseEntity.ok(data);
    case Failure(var error) -> ResponseEntity.badRequest().body(error);
};
```

## Testing

### Use Modern Testing Features

```java
// ✅ GOOD - Descriptive test names
@Test
void shouldReturnCachedResponseWhenValidCacheExists() {
    // given
    var cachedResponse = new CachedResponse("key", "data", Instant.now());
    when(cache.get("key")).thenReturn(cachedResponse);

    // when
    var result = service.getResponse("key");

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getData()).isEqualTo("data");
}

// ✅ GOOD - Use parameterized tests
@ParameterizedTest
@ValueSource(strings = {"", "  ", "\t", "\n"})
void shouldRejectBlankUrls(String url) {
    assertThatThrownBy(() -> extractor.extract(url))
        .isInstanceOf(IllegalArgumentException.class);
}
```

## Code Organization

### Package Structure

```
com.browserapi/
├── config/           # Configuration classes
├── domain/           # Domain models, entities
├── service/          # Business logic
├── repository/       # Data access
├── controller/       # REST endpoints
├── dto/              # Request/response DTOs (use records)
├── exception/        # Custom exceptions
└── util/             # Utility classes
```

### File Organization

- One public class per file
- Order: static fields → instance fields → constructors → methods
- Group related methods together
- Keep files under 300 lines

## Comments & Documentation

### Production-Ready Comments Only

Write comments that would be valuable in production code. Avoid temporary, obvious, or debug comments.

```java
// ✅ GOOD - Production-worthy comments
/**
 * Extracts CSS rules that apply to the given element.
 * Includes both inline styles and matching stylesheet rules.
 *
 * @param element the target element
 * @return deduplicated CSS rules sorted by specificity
 */
public List<CSSRule> extractStyles(Element element) {
    // Shadow DOM requires special handling for style isolation
    if (element.shadowRoot() != null) {
        return extractShadowStyles(element);
    }
    return extractNormalStyles(element);
}

// ✅ GOOD - Explaining non-obvious business logic
// SQLite only supports one writer at a time, pool size must be 1
hikari.setMaximumPoolSize(1);

// ✅ GOOD - Warning about important constraints
// Do not change: Playwright sessions must be closed on the same thread that created them
threadLocal.get().close();

// ❌ BAD - Obvious comment
// Get the URL
String url = request.getUrl();

// ❌ BAD - Commented-out code (delete instead)
// String oldMethod = doSomethingOld();
// return oldMethod.transform();

// ❌ BAD - TODO/FIXME in production
// TODO: fix this later
// FIXME: temporary hack

// ❌ BAD - Debug/noise comments
// System.out.println("DEBUG: entering method");
// calling the service
var result = service.process();
```

### When to Write Comments

```
✅ Write comments for:
- Complex algorithms or business logic
- Non-obvious technical decisions
- Workarounds for library bugs
- Performance-critical sections
- Security-sensitive code
- Public API documentation (Javadoc)

❌ Don't write comments for:
- Obvious code (let the code speak)
- Commented-out code (use git history)
- TODOs or FIXMEs (create tickets instead)
- Change history (use git commits)
- Debug statements
```

## What NOT to Do

### Don't Generate Documentation Files Unnecessarily

```
❌ BAD - Don't create these unless explicitly requested:
- README.md for every package
- CONTRIBUTING.md
- CHANGELOG.md (unless setting up releases)
- API.md (use OpenAPI/Swagger instead)
- Excessive inline documentation

✅ GOOD - Do create:
- Javadoc for public APIs
- Production-worthy code comments (see above)
- OpenAPI annotations for endpoints
```

### Don't Over-Engineer

```java
// ❌ BAD - Unnecessary abstraction
public interface StringProcessor {
    String process(String input);
}
public class UpperCaseProcessor implements StringProcessor {...}
public class LowerCaseProcessor implements StringProcessor {...}
// ... when you only need: input.toUpperCase()

// ✅ GOOD - Keep it simple
public String normalize(String input) {
    return input.trim().toLowerCase();
}
```

### Don't Use Mutable Collections Unnecessarily

```java
// ❌ BAD
public List<String> getUrls() {
    return urls; // exposes internal mutable state
}

// ✅ GOOD
public List<String> getUrls() {
    return List.copyOf(urls); // defensive copy
}
```

## Git Commit Conventions

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- **feat**: New feature
- **fix**: Bug fix
- **refactor**: Code change that neither fixes a bug nor adds a feature
- **perf**: Performance improvement
- **test**: Adding or updating tests
- **docs**: Documentation only changes
- **style**: Code style changes (formatting, missing semicolons, etc.)
- **chore**: Changes to build process or auxiliary tools
- **build**: Changes to build system or dependencies
- **ci**: Changes to CI configuration files

### Examples

```bash
feat(extraction): add HTML extraction strategy

Implement HTMLExtractor that supports innerHTML and outerHTML
extraction modes with multiple selector support.

Closes #006

---

fix(browser): handle timeout in navigation helper

Add proper timeout handling and retry logic to prevent
session hangs when pages fail to load.

---

refactor(cache): use sealed interface for cache results

Replace Optional-based API with sealed Success/Failure
result type for better type safety.

---

chore(deps): upgrade playwright to 1.40.0
```

### Commit Guidelines

- Write in imperative mood: "add feature" not "added feature"
- First line max 72 characters
- Separate subject from body with blank line
- Use body to explain *what* and *why*, not *how*
- Reference issues and tickets in footer
- One logical change per commit
- Commit working code (build should pass)

### When to Commit

```
✅ Commit after:
- Completing a logical unit of work
- All tests pass
- Code compiles without warnings
- Feature is functional (even if incomplete)

❌ Don't commit:
- Broken code
- Commented-out code
- Temporary debugging statements
- Half-finished thoughts
```

## Code Review Checklist

Before considering code complete:

- [ ] Code compiles without warnings
- [ ] All tests pass
- [ ] New tests added for new functionality
- [ ] No commented-out code
- [ ] No debug logging statements
- [ ] Javadoc added for public methods
- [ ] Proper error handling
- [ ] Used records for DTOs where applicable
- [ ] Used Optional instead of null
- [ ] Immutable by default
- [ ] Proper commit message with type prefix

---

**Version**: 1.0.0
**Last Updated**: 2025-12-12
**Project**: Browser API
