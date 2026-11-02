# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Versions match the
`<version>` in `pom.xml` and the git tags, which CI creates when a new version reaches `main`.

## [2.0.2] - 2026-11-02

### Added
- Tests for `ChunkUploadService`, `ChunkUploadController`, `MainLayout` and the upload and
  transcription flow in `TranscriberView`. Line coverage goes from 40% to 97%.

### Changed
- The integration tests are back on. They were disabled because the full application needed a live
  identity provider, and now run the real application and `SecurityConfig` against a stub OIDC
  provider. They cover the PKCE login redirect, that pages require sign-in, and the upload API. No
  tests are skipped any more.
- README: the welcome page also requires sign-in. The README had said it was public.

### Removed
- Unused test helpers `TestApplication` and `TestSecurityConfig`.

### Security
- Sessions are tracked with cookies only (`server.servlet.session.tracking-modes=cookie`), so session
  ids no longer appear in redirect URLs as `;jsessionid=...`.

## [2.0.1] - 2026-11-02

### Added
- README badges for tests, line coverage and version.
- `CI` GitHub Actions workflow: runs the tests with JaCoCo coverage, publishes the badge
  data to the `badges` branch, and tags each new `pom.xml` version on `main`.
- `Snyk` GitHub Actions workflow: runs `snyk test` and `snyk code test` on pushes to `main`,
  on pull requests and weekly. Fails on high or critical issues.
- Git `pre-push` hook (`.githooks/pre-push`) running the same Snyk scans locally.
- JaCoCo coverage report, written to `target/site/jacoco/` during `test`.
- Maven wrapper (`./mvnw`, Maven 3.9.16), so a local Maven install is no longer needed.
- Tests for the production security configuration (CSRF on the upload API, logout).
- This changelog.

### Changed
- `pom.xml` version set to `2.0.1`. It still read `1.0-SNAPSHOT` at the `2.0.0` tag.
- README, Docker and testing guides updated for Keycloak, Java 21 and the current build.

### Security
- Enabled Spring Security CSRF protection on the state-changing chunked upload API calls.
  The browser uploader now sends the session's CSRF token. Vaadin requests keep using
  Vaadin's built-in CSRF protection.
- Upgraded `jackson-databind` to 3.1.8 (SNYK-JAVA-TOOLSJACKSONCORE-21761838, critical).
- Removed the unused `commons-fileupload` dependency (SNYK-JAVA-COMMONSFILEUPLOAD-10363252, high).
- Upload ids must now be well-formed UUIDs, and files are resolved only inside their own
  upload directory.

## [2.0.0] - 2026-11-02

### Added
- Chunked uploads: large audio files are sent in 50MB chunks, so they get through
  Cloudflare's 100MB request limit.
- RP-initiated logout, so logging out also ends the Keycloak session.
- Docker Compose service joins the external `tnra-shared` network.

### Changed
- Authentication moved from Okta to Keycloak using Spring Security's OAuth2 client
  (authorization code flow with PKCE, public client, no client secret).
- Upgraded to Spring Boot 4.1.2, Vaadin 25.3.4, Java 21 and Jackson 3.

### Removed
- `okta-spring-boot-starter`.

## [1.0] - 2026-01-11

### Added
- Vaadin web UI for uploading MP3/WAV files and naming two speakers.
- AssemblyAI transcription with speaker labels and transcript download.
- OAuth2/OIDC login.
- Test suite and Docker deployment.

[2.0.2]: https://github.com/dogeared/mp3-transcription-app/compare/2.0.1...2.0.2
[2.0.1]: https://github.com/dogeared/mp3-transcription-app/compare/2.0.0...2.0.1
[2.0.0]: https://github.com/dogeared/mp3-transcription-app/compare/1.0...2.0.0
[1.0]: https://github.com/dogeared/mp3-transcription-app/releases/tag/1.0
