# MP3 Transcriber

A secure web application for transcribing MP3 files using AssemblyAI, built with Spring Boot, Vaadin Flow, and OAuth2/OIDC authentication via Okta.

## Features

- **Secure Authentication**: OAuth2/OIDC with PKCE using Okta
- **File Upload**: Drag-and-drop MP3/WAV file upload with validation (up to 500MB)
- **Speaker Diarization**: Assign custom names to speakers in the audio
- **Audio Transcription**: Upload and transcribe audio files using AssemblyAI's API
- **Progress Indicator**: Progress updates during transcription
- **Download Transcript**: Download completed transcription as a text file
- **Modern UI**: Clean, responsive interface built with Vaadin Flow
- **Direct API Integration**: Uses AssemblyAI REST API directly

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Okta Developer Account
- AssemblyAI API Key

## Setup Instructions

### 1. Clone the Repository

```bash
git clone <repository-url>
cd cursor-transcription-app
```

### 2. Configure Okta

1. Sign up for a free [Okta Developer Account](https://developer.okta.com/)
2. Create a new application:
   - Application Type: Web Application
   - Grant Types: Authorization Code, PKCE
   - Sign-in redirect URIs: `http://localhost:8080/login/oauth2/code/okta`
   - Sign-out redirect URIs: `http://localhost:8080/`
3. Note down your:
   - Client ID
   - Client Secret
   - Okta Domain (e.g., `https://dev-12345.okta.com`)

### 3. Get AssemblyAI API Key

1. Sign up for [AssemblyAI](https://www.assemblyai.com/)
2. Get your API key from the dashboard

### 4. Configure Environment Variables

Set the following environment variables:

```bash
export OKTA_CLIENT_ID=your_okta_client_id
export OKTA_CLIENT_SECRET=your_okta_client_secret
export OKTA_ISSUER_URI=https://your-okta-domain.okta.com/oauth2/default
export ASSEMBLYAI_API_KEY=your_assemblyai_api_key
```

Alternatively, you can update the values directly in `src/main/resources/application.yml`.

### 5. Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

The application will be available at `http://localhost:8080`

## Usage

1. **Access Application**: Navigate to `http://localhost:8080`
2. **Authentication**: Click "Get Started" - you'll be automatically redirected to Okta for authentication
3. **Login**: Enter your Okta credentials
4. **Redirect Back**: After successful authentication, you'll be redirected to the transcriber interface
5. **Speaker Names**: Enter names for the two speakers in your audio file
6. **Upload File**: Drag and drop or click to upload your MP3 or WAV file (max 500MB)
7. **Start Transcription**: Click "Start Transcription" to begin processing
8. **Monitor Progress**: Watch the progress indicator for updates
9. **Download**: Once complete, click "Download Transcript" to get your transcription
12. **Logout**: Use the logout button in the top navigation

## Architecture

- **Backend**: Spring Boot 3.2.3 with Spring Security
- **Frontend**: Vaadin Flow 24.3.5
- **Authentication**: OAuth2/OIDC with PKCE
- **Transcription**: Direct AssemblyAI REST API integration
- **HTTP Client**: OkHttp for REST API calls
- **Security**: Spring Security with OAuth2 client

## Project Structure

```
src/
├── main/
│   ├── java/com/example/transcriber/
│   │   ├── TranscriberApplication.java          # Main application class
│   │   ├── config/
│   │   │   ├── SecurityConfig.java              # Security configuration
│   │   │   └── CustomAuthorizationRequestResolver.java # PKCE support
│   │   ├── service/
│   │   │   └── TranscriptionService.java        # AssemblyAI integration
│   │   └── view/
│   │       ├── LoginView.java                   # Login page
│   │       ├── MainLayout.java                  # Application layout
│   │       └── TranscriberView.java             # Main transcription UI
│   └── resources/
│       └── application.yml                      # Configuration
```

## Configuration

### OAuth2/OIDC Settings

Spring Boot auto-configures OIDC with PKCE support. The configuration uses:
- Standard OIDC discovery from issuer URI
- Automatic redirect to Okta's authorization server
- PKCE enabled by default for enhanced security
- Session-based authentication after successful login
- Automatic token refresh handled by Spring Security

### AssemblyAI Settings

- Speaker diarization is enabled by default
- Expects 2 speakers maximum
- Supports files up to 500MB
- **Transcription**: Uses REST API at `https://api.assemblyai.com/v2`
- **File Upload**: Direct upload to AssemblyAI's upload endpoint
- **Authentication**: API key passed in Authorization header

### File Upload

- Accepts MP3 and WAV files
- Maximum file size: 500MB
- Files are temporarily stored during processing

## Development

To run in development mode:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Security Considerations

- All endpoints except the welcome page are protected by OAuth2/OIDC
- Automatic redirect to Okta for authentication
- PKCE (Proof Key for Code Exchange) is enabled by default in Spring Security
- Session management handled by Spring Security
- CSRF protection is disabled for API endpoints
- File uploads are validated for type and size
- Temporary files are cleaned up after processing

## Troubleshooting

### Common Issues

1. **OAuth2 Configuration Error**
   - Verify Okta domain, client ID, and secret
   - Check redirect URIs in Okta application settings

2. **AssemblyAI API Error**
   - Verify API key is correct
   - Check AssemblyAI account credits/limits

3. **File Upload Issues**
   - Ensure file is MP3 format
   - Check file size (max 100MB)
   - Verify sufficient disk space

### Logs

Enable debug logging by setting:
```yaml
logging:
  level:
    com.example.transcriber: DEBUG
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License. 