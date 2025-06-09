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
│   └── SecurityConfigTest.java
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
**Purpose**: Test Spring Security configuration

**Coverage**:
- ✅ Public endpoint accessibility
- ✅ Protected endpoint authentication requirements
- ✅ OAuth2 login flow initiation
- ✅ Static resource access permissions
- ✅ Logout functionality

### 3. Integration Tests

#### 🔹 Application Tests (`TranscriberApplicationIntegrationTest`)
**Purpose**: Test complete application startup and integration

**Coverage**:
- ✅ Spring context loading
- ✅ Application startup with test properties
- ✅ Endpoint accessibility and security
- ✅ Service bean injection
- ✅ Health check endpoints
- ✅ Vaadin resource availability

## Running Tests

### 🚀 All Tests
```bash
# Run complete test suite
mvn test

# Run with detailed output
mvn test -X

# Run with specific profile
mvn test -Dspring.profiles.active=test
```

### 🎯 Specific Test Categories

#### Unit Tests Only
```bash
# Run all unit tests (excludes integration tests)
mvn test -Dtest="*Test" -Dtest.exclude="*IntegrationTest"

# Run specific unit test
mvn test -Dtest=TranscriptionServiceTest
mvn test -Dtest=TranscriberViewTest
```

#### Integration Tests Only
```bash
# Run integration tests
mvn test -Dtest="*IntegrationTest"
```

#### Test by Package
```bash
# Service tests
mvn test -Dtest="com.example.transcriber.service.*"

# View tests
mvn test -Dtest="com.example.transcriber.view.*"

# DTO tests
mvn test -Dtest="com.example.transcriber.dto.*"
```

### 🔍 Test with Coverage
```bash
# Run tests with coverage report
mvn test jacoco:report

# Coverage report location: target/site/jacoco/index.html
```

## Test Configuration

### Test Properties
Tests use `src/test/resources/application-test.yml` with:
- ✅ Random port assignment
- ✅ Test OAuth2 credentials
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

### GitHub Actions / CI Pipeline
```yaml
# Example CI configuration
- name: Run Tests
  run: mvn test
  
- name: Run Integration Tests
  run: mvn test -Dtest="*IntegrationTest"
  
- name: Generate Coverage Report
  run: mvn jacoco:report
```

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