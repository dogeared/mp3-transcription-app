# MP3 Transcriber

[![Tests](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/dogeared/mp3-transcription-app/badges/tests.json)](https://github.com/dogeared/mp3-transcription-app/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/dogeared/mp3-transcription-app/badges/coverage.json)](https://github.com/dogeared/mp3-transcription-app/actions/workflows/ci.yml)
[![Version](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/dogeared/mp3-transcription-app/badges/version.json)](CHANGELOG.md)

A web application for transcribing MP3 and WAV recordings with [AssemblyAI](https://www.assemblyai.com/),
built with Spring Boot and Vaadin Flow. Users sign in through [Keycloak](https://www.keycloak.org/) using
OAuth2/OIDC.

## Features

- **Keycloak sign-in**: authorization code flow with PKCE (S256), public client, no client secret
- **Single sign-out**: logging out also ends the Keycloak session
- **Large file uploads**: files are uploaded in 50MB chunks, so they get through proxy limits such as
  Cloudflare's 100MB request cap
- **Speaker names**: name the two speakers, and the transcript uses those names
- **Progress updates** while AssemblyAI processes the file
- **Transcript download** as a text file

## Tech stack

| Area           | Technology                                                          |
|----------------|---------------------------------------------------------------------|
| Runtime        | Java 21                                                             |
| Backend        | Spring Boot 4.1, Spring Security 7 (OAuth2 client)                  |
| UI             | Vaadin Flow 25                                                      |
| Identity       | Keycloak (any OIDC provider with PKCE support works)                |
| Transcription  | AssemblyAI REST API, called through OkHttp; JSON handled by Jackson 3 |
| Build          | Maven, via the bundled wrapper (`./mvnw`)                           |

## Prerequisites

- Java 21
- A Keycloak realm you can add a client to
- An AssemblyAI API key
- Docker, if you deploy with Docker Compose
- The [Snyk CLI](https://docs.snyk.io/snyk-cli), if you contribute (the pre-push hook uses it)

Maven doesn't need to be installed; `./mvnw` downloads the right version.

## Setup

### 1. Create the Keycloak client

In the Keycloak admin console, open your realm, go to **Clients** and click **Create client**:

1. **General settings**: Client type **OpenID Connect**, Client ID `mp3-transcriber`.
2. **Capability config**: turn **Client authentication off**, which makes it a public client.
   Check only **Standard flow**. If a **PKCE Method** field is shown, set it to **S256**.
3. **Login settings** (replace `https://APP_HOST` with your app's public URL):

   | Field                           | Value                                         |
   |---------------------------------|-----------------------------------------------|
   | Root URL / Home URL             | `https://APP_HOST`                            |
   | Valid redirect URIs             | `https://APP_HOST/login/oauth2/code/keycloak` |
   | Valid post logout redirect URIs | `https://APP_HOST/`                           |
   | Web origins                     | `https://APP_HOST`                            |

4. If you didn't set PKCE in step 2: on the **Advanced** tab, set
   **Proof Key for Code Exchange Code Challenge Method** to **S256**.
5. On the **Client scopes** tab, make sure `profile` and `email` are listed as **Default**.

Then create users under **Users**, setting a non-temporary password on the **Credentials** tab.

### 2. Configure environment variables

| Variable              | Description                                                         |
|-----------------------|---------------------------------------------------------------------|
| `KEYCLOAK_ISSUER_URI` | Realm issuer, e.g. `https://auth.example.com/realms/my-realm`       |
| `KEYCLOAK_CLIENT_ID`  | Client ID from step 1, e.g. `mp3-transcriber`                       |
| `ASSEMBLYAI_API_KEY`  | Your AssemblyAI API key                                             |

`KEYCLOAK_ISSUER_URI` must exactly match the `issuer` value at
`<issuer>/.well-known/openid-configuration`. The app checks that discovery document at startup, and the
issuer in every token is compared against it. Use the public URL the browser sees, even if the app could
reach Keycloak on an internal hostname.

For Docker Compose, copy `docker.env.sample` to `.env` and fill it in.

### 3. Build and run

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. To run it locally, add
`http://localhost:8080/login/oauth2/code/keycloak` to the client's redirect URIs.

To deploy with Docker, see [README-Docker.md](README-Docker.md).

## Usage

1. Open the app and click **Get Started**. You're sent to Keycloak to sign in.
2. Enter names for the two speakers. They default to "Speaker 1" and "Speaker 2".
3. Choose an MP3 or WAV file. It uploads in chunks with a progress bar.
4. Click **Start Transcription** and wait for AssemblyAI to finish.
5. Click **Download Transcript**.
6. Use **Logout** in the header to sign out of both the app and Keycloak.

## Project structure

```
src/main/java/com/example/transcriber/
├── TranscriberApplication.java       # Spring Boot entry point and Vaadin app shell (@Push)
├── config/SecurityConfig.java        # OAuth2 login, CSRF on the upload API, OIDC logout
├── controller/ChunkUploadController.java  # REST API for chunked uploads (/api/upload/**)
├── dto/                              # Upload and AssemblyAI transcript DTOs
├── service/
│   ├── ChunkUploadService.java       # Stores chunks and assembles uploaded files
│   └── TranscriptionService.java     # AssemblyAI upload, transcription and polling
└── view/
    ├── WelcomeView.java              # Landing page
    ├── MainLayout.java               # Header with signed-in user and logout
    └── TranscriberView.java          # Upload, transcription and download UI
frontend/chunked-upload.js            # Browser-side chunked uploader
src/main/resources/application.properties  # App, upload and Keycloak configuration
```

## Security

- Every page except the welcome page requires sign-in.
- Sign-in uses the authorization code flow with PKCE (S256). The app is a public client, so there's
  no client secret to manage.
- Logout uses OIDC RP-initiated logout, ending the Keycloak session as well.
- The chunked upload API is protected by Spring Security's CSRF token, which the uploader sends
  with each request. Vaadin's own requests use Vaadin's built-in CSRF protection.
- Upload sessions belong to the user who created them. Upload ids must be server-generated UUIDs,
  and uploaded file names are sanitized and kept inside their upload directory.

## Development

### Tests and coverage

```bash
./mvnw test
```

This runs the suite and writes a JaCoCo coverage report to `target/site/jacoco/index.html`.
See [README-Testing.md](README-Testing.md) for details.

### Snyk pre-push hook

The repository ships a `pre-push` hook that runs `snyk test` (dependencies) and `snyk code test`
(static analysis), and blocks the push on high or critical issues. Enable it once per clone:

```bash
npm install --global snyk   # if you don't have the CLI
snyk auth
git config core.hooksPath .githooks
```

To skip it for a single push, use `git push --no-verify`.

### Continuous integration

| Workflow | Runs on | What it does |
|----------|---------|--------------|
| [`CI`](.github/workflows/ci.yml) | pushes to `main`, pull requests | Runs the tests with coverage. On `main`, it publishes the badge data to the `badges` branch and creates a git tag for a new `pom.xml` version. |
| [`Snyk`](.github/workflows/snyk.yml) | pushes to `main`, pull requests, weekly | Runs `snyk test` and `snyk code test`; fails on high or critical issues. Needs the `SNYK_TOKEN` repository secret. |

### Releasing

The version in `pom.xml` is the release version, and git tags follow it:

1. Bump `<version>` in `pom.xml`.
2. Add a section for it to [CHANGELOG.md](CHANGELOG.md).
3. Merge to `main`. When CI passes, it tags the commit with the new version.

## Troubleshooting

- **Login fails or loops back to Keycloak**: check that `KEYCLOAK_ISSUER_URI` matches the realm's
  `issuer` exactly, and that the redirect URI is registered on the client.
- **"Invalid redirect_uri" from Keycloak**: behind a reverse proxy or tunnel, the app builds the redirect
  from the `X-Forwarded-*` headers (`server.forward-headers-strategy=framework`). Make sure the proxy
  sends them, so the redirect starts with `https://`.
- **Upload fails with 403**: the uploader's CSRF token is tied to the session. Reload the page and try again.
- **Transcription fails**: check the AssemblyAI API key and your account's credits.

For more logging, set `LOGGING_LEVEL_COM_EXAMPLE_TRANSCRIBER=DEBUG`.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

This project is licensed under the MIT License.
