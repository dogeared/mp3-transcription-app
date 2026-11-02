# Testing Guide for MP3 Transcriber Application

This document provides comprehensive information about the testing strategy and test suite for the MP3 Transcriber application.

## Test Structure

The test suite is organized into several categories:

### 📁 Test Directory Structure
```
src/test/java/com/example/transcriber/
├── dto/                    # DTO Unit Tests
│   └── TranscriptResponseTest.java
├── service/                # Service Unit Tests
│   └── TranscriptionServiceTest.java
├── view/                   # View Unit Tests
│   ├── WelcomeViewTest.java
│   └── TranscriberViewTest.java
├── config/                 # Configuration Tests
│   ├── SecurityConfigTest.java
│   ├── SecurityConfigCsrfTest.java
│   └── TestSecurityConfig.java
├── integration/            # Integration Tests
│   └── TranscriberApplicationIntegrationTest.java
└── TestSuite.java         # Test Documentation
```

## Test Categories

### 1. Unit Tests

#### 🔹 DTO Tests (`TranscriptResponseTest`)
**Purpose**: Test data transfer objects and JSON serialization/deserialization

**Coverage**:
- ✅ JSON deserialization from AssemblyAI API responses
- ✅ JSON serialization for internal processing
- ✅ Error response handling
- ✅ Unknown property ignoring (`@JsonIgnoreProperties`)
- ✅ Nested `Utterance` object handling

**Key Test Cases**:
- Complete transcript response with utterances
- Error responses with error messages
- Empty/minimal responses
- Standalone utterance objects

#### 🔹 Service Tests (`TranscriptionServiceTest`)
**Purpose**: Test business logic with mocked external dependencies

**Coverage**:
- ✅ File upload to AssemblyAI
- ✅ Transcription submission and polling
- ✅ Error handling (upload failures, API errors)
- ✅ Async operation handling with `CompletableFuture`
- ✅ Progress callback mechanisms
- ✅ Speaker name handling (including empty names)

**Key Features**:
- Uses `MockWebServer` for HTTP interaction testing
- Tests real async behavior with proper timeout handling
- Verifies HTTP request structure and headers
- Tests error propagation and recovery

#### 🔹 View Tests (`WelcomeViewTest`, `TranscriberViewTest`)
**Purpose**: Test UI component initialization and configuration

**Coverage**:
- ✅ Component presence and configuration
- ✅ UI layout and styling
- ✅ Default values and field configurations
- ✅ Button states and themes
- ✅ Component hierarchy and structure

**Key Features**:
- Tests Vaadin component initialization
- Verifies UI component properties and styling
- Tests service injection and mocking
- Component accessibility testing

### 2. Configuration Tests

#### 🔹 Security Tests (`SecurityConfigTest`)
**Purpose**: Test access rules using a form-login stand-in for the OAuth2 setup

**Coverage**:
- ✅ Public endpoint accessibility
- ✅ Protected endpoint authentication requirements (redirect to `/login`)
- ✅ Logout redirection

#### 🔹 Production Security Tests (`SecurityConfigCsrfTest`)
**Purpose**: Test the real `SecurityConfig` against the chunked upload API

**Coverage**:
- ✅ Upload API calls without a CSRF token are rejected (403)
- ✅ Upload API calls with a CSRF token are accepted
- ✅ `GET /logout` still logs out

**Key Features**:
- Uses `oidcLogin()` and `csrf()` from Spring Security Test
- Mocks the `ClientRegistrationRepository`, so no identity provider is needed

### 3. Integration Tests

#### 🔹 Application Tests (`TranscriberApplicationIntegrationTest`)
**Purpose**: Test complete application startup and integration

> These tests are currently `@Disabled`: loading the full context needs the OAuth2 beans that the
> test profile excludes.

**Coverage**:
- ✅ Spring context loading
- ✅ Application startup with test properties
- ✅ Endpoint accessibility and security
- ✅ Service bean injection
- ✅ Health check endpoints
- ✅ Vaadin resource availability

## Running Tests

### 🚀 All Tests
The project includes the Maven wrapper, so use `./mvnw` (no local Maven install needed).

```bash
# Run complete test suite (also writes the JaCoCo coverage report)
./mvnw test

# Run with detailed output
./mvnw test -X
```

### 🎯 Specific Test Categories

#### Unit Tests Only
```bash
# Run all unit tests (excludes integration tests)
./mvnw test -Dtest='!*IntegrationTest'

# Run specific unit test
./mvnw test -Dtest=TranscriptionServiceTest
./mvnw test -Dtest=TranscriberViewTest
```

#### Integration Tests Only
```bash
# Run integration tests
./mvnw test -Dtest="*IntegrationTest"
```

#### Test by Package
```bash
# Service tests
./mvnw test -Dtest="com.example.transcriber.service.*"

# View tests
./mvnw test -Dtest="com.example.transcriber.view.*"

# DTO tests
./mvnw test -Dtest="com.example.transcriber.dto.*"
```

### 🔍 Test with Coverage
JaCoCo is configured in `pom.xml` and runs as part of `test`:

```bash
./mvnw test

# HTML report: target/site/jacoco/index.html
# CSV report (used for the coverage badge): target/site/jacoco/jacoco.csv
```

## Test Configuration

### Test Properties
Tests use `src/test/resources/application-test.yml` with:
- ✅ Random port assignment
- ✅ OAuth2 client and Spring Boot security auto-configuration excluded
- ✅ An in-memory test user (`testuser` / `testpass`)
- ✅ Reduced file size limits
- ✅ Minimal logging for cleaner output
- ✅ Test-specific API keys

### Key Test Dependencies
- **JUnit 5**: Core testing framework
- **Mockito**: Mocking framework
- **Spring Boot Test**: Integration testing
- **Spring Security Test**: Security testing
- **Vaadin TestBench**: UI component testing
- **OkHttp MockWebServer**: HTTP interaction testing
- **Awaitility**: Async testing utilities

## Test Patterns and Best Practices

### 🏗️ Mocking Strategy
- **External APIs**: Mocked using `MockWebServer`
- **Spring Services**: Mocked using `@Mock` annotations
- **UI Components**: Tested with real Vaadin components
- **Security**: Tested with `@WithMockUser` and `@WithAnonymousUser`

### ⚡ Async Testing
```java
// Example: Testing async operations
CompletableFuture<String> future = service.transcribeFile(...);
future.whenComplete((result, throwable) -> {
    // Verify results
    latch.countDown();
});
assertTrue(latch.await(10, TimeUnit.SECONDS));
```

### 🔧 Component Testing
```java
// Example: Testing Vaadin components
TextField field = getFieldByAccessor("speaker1Field");
assertEquals("Tes", field.getValue());
assertEquals("300px", field.getWidth());
```

## Continuous Integration

### GitHub Actions
The [`CI` workflow](.github/workflows/ci.yml) runs on every pull request and every push to `main`:

1. Runs `./mvnw test` on Java 21, which produces the Surefire and JaCoCo reports.
2. Builds the badge data with [`.github/scripts/badges.py`](.github/scripts/badges.py): test counts,
   line coverage, and the `pom.xml` version. Results also appear in the job summary.
3. Uploads the test and coverage reports as the `reports` artifact.
4. On `main`, pushes the badge JSON to the `badges` branch, which the README badges read through
   shields.io. It also tags a new `pom.xml` version.

The job fails if any test fails, but the badges are still published, so the tests badge shows
the failure.

The [`Snyk` workflow](.github/workflows/snyk.yml) runs the dependency and code scans. The same
scans run locally in the `pre-push` hook (see the [README](README.md#snyk-pre-push-hook)).

## Troubleshooting Tests

### Common Issues

1. **Test Fails with "UI.getCurrent() returns null"**
   ```java
   // Solution: Mock UI in test setup
   @Mock private UI mockUI;
   @BeforeEach
   void setUp() {
       UI.setCurrent(mockUI);
   }
   ```

2. **Integration Test Port Conflicts**
   ```yaml
   # Solution: Use random port in test properties
   server:
     port: 0
   ```

3. **MockWebServer Connection Issues**
   ```java
   // Solution: Ensure proper cleanup
   @AfterEach
   void tearDown() throws IOException {
       mockWebServer.shutdown();
   }
   ```

4. **Security Test Authentication Issues**
   ```java
   // Solution: Use proper security test annotations
   @WithMockUser
   @WithAnonymousUser
   ```

## Test Metrics and Goals

### Coverage Targets
- **Service Layer**: >90% line coverage
- **View Layer**: >80% component coverage
- **Configuration**: >95% path coverage
- **Integration**: All critical paths tested

### Performance Benchmarks
- Unit tests: <1 second each
- Integration tests: <30 seconds total
- Full test suite: <2 minutes

## Adding New Tests

### For New Services
1. Create test class in `src/test/java/.../service/`
2. Use `@ExtendWith(MockitoExtension.class)`
3. Mock external dependencies with `MockWebServer`
4. Test async operations with proper timeout handling

### For New Views
1. Create test class in `src/test/java/.../view/`
2. Mock service dependencies
3. Test component initialization and configuration
4. Use reflection for private field access when needed

### For New Integration Scenarios
1. Add tests to existing `TranscriberApplicationIntegrationTest`
2. Use `@TestPropertySource` for specific configurations
3. Test endpoint accessibility and security
4. Verify service integration and dependency injection 