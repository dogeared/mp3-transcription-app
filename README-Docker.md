# Docker Deployment Guide

This document provides instructions for building and running the MP3 Transcriber application using Docker.

## Prerequisites

- Docker installed on your system
- Docker Compose (optional, for easier management)

## Building the Docker Image

### Option 1: Build with Docker directly

```bash
# Build the Docker image
docker build -t mp3-transcriber:latest .

# Run the container
docker run -d \
  --name mp3-transcriber-app \
  -p 8080:8080 \
  -e OKTA_CLIENT_ID="your-client-id" \
  -e OKTA_CLIENT_SECRET="your-client-secret" \
  -e OKTA_ISSUER_URI="https://your-domain.okta.com/oauth2/default" \
  -e ASSEMBLYAI_API_KEY="your-assemblyai-api-key" \
  mp3-transcriber:latest
```

### Option 2: Use Docker Compose (Recommended)

1. **Create environment file** (REQUIRED):
   ```bash
   cp docker.env.sample .env
   # Edit .env with your actual values - ALL variables are required!
   ```

2. **Build and run with Docker Compose**:
   ```bash
   # Build and start the application
   docker-compose up -d --build
   
   # View logs
   docker-compose logs -f
   
   # Stop the application
   docker-compose down
   ```

## Environment Variables

The following environment variables **MUST** be configured:

| Variable | Description | Required |
|----------|-------------|----------|
| `OKTA_CLIENT_ID` | Okta OAuth2 Client ID | ✅ Yes |
| `OKTA_CLIENT_SECRET` | Okta OAuth2 Client Secret | ✅ Yes |
| `OKTA_ISSUER_URI` | Okta OAuth2 Issuer URI | ✅ Yes |
| `ASSEMBLYAI_API_KEY` | AssemblyAI API Key | ✅ Yes |
| `JAVA_OPTS` | JVM Options | ❌ No (has default) |

**⚠️ IMPORTANT**: The application will not start without the required environment variables!

## Accessing the Application

Once the container is running, access the application at:
- **Application**: http://localhost:8080
- **Health Check**: http://localhost:8080/actuator/health

## Container Management

### View running containers
```bash
docker ps
```

### View application logs
```bash
# Docker
docker logs mp3-transcriber-app

# Docker Compose
docker-compose logs -f mp3-transcriber
```

### Stop the application
```bash
# Docker
docker stop mp3-transcriber-app
docker rm mp3-transcriber-app

# Docker Compose
docker-compose down
```

### Access container shell (for debugging)
```bash
# Docker
docker exec -it mp3-transcriber-app sh

# Docker Compose
docker-compose exec mp3-transcriber sh
```

## File Uploads

The application handles file uploads internally. In the Docker Compose setup, a volume is mounted at `./uploads:/tmp/uploads` for persistent storage of temporary files.

## Health Checks

The container includes health checks that verify the application is responding correctly:
- **Interval**: 30 seconds
- **Timeout**: 10 seconds
- **Retries**: 3
- **Start Period**: 60 seconds

## Security Notes

- The container runs as a non-root user (appuser:appgroup)
- Sensitive configuration values should be provided via environment variables
- Consider using Docker secrets or external secret management for production deployments

## Troubleshooting

### Common Issues

1. **Container won't start**: Check logs with `docker logs mp3-transcriber-app`
   - Most common cause: Missing required environment variables
2. **Port already in use**: Change the host port in docker-compose.yml or Docker run command
3. **OAuth2 authentication fails**: Verify your Okta configuration and credentials
4. **API calls fail**: Verify your AssemblyAI API key
5. **Missing environment variables**: Ensure all required variables are set in your .env file

### Debug Mode

To run with debug logging:
```bash
docker run -d \
  --name mp3-transcriber-app \
  -p 8080:8080 \
  -e LOGGING_LEVEL_COM_EXAMPLE_TRANSCRIBER=DEBUG \
  mp3-transcriber:latest
``` 