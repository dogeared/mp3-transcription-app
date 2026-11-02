# Docker Deployment Guide

This document provides instructions for building and running the MP3 Transcriber application using Docker.

## Prerequisites

- Docker with the Compose plugin
- Java 21 to build the jar. Maven is not needed; the project includes `./mvnw`.
- A Keycloak client set up as described in the [README](README.md#1-create-the-keycloak-client)

## Build the application jar

The image copies a pre-built jar from `target/`, so build it first. The `production` profile
bundles the Vaadin frontend:

```bash
./mvnw -B -Pproduction clean package -DskipTests
```

Without Java 21 on the host, build in a container instead:

```bash
docker run --rm -v "$PWD":/src -v "$HOME/.m2":/root/.m2 -w /src maven:3.9-eclipse-temurin-21 \
  sh -c "mvn -B -Pproduction clean package -DskipTests && chown -R $(id -u):$(id -g) /src"
```

## Building the Docker Image

### Option 1: Build with Docker directly

```bash
# Build the Docker image
docker build -t mp3-transcriber:latest .

# Run the container
docker run -d \
  --name mp3-transcriber-app \
  -p 8080:8080 \
  -e KEYCLOAK_ISSUER_URI="https://auth.example.com/realms/my-realm" \
  -e KEYCLOAK_CLIENT_ID="mp3-transcriber" \
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
   # Build and start the application (after building the jar)
   docker compose up -d --build

   # View logs
   docker compose logs -f

   # Stop the application
   docker compose down
   ```

   The Compose file publishes the app on `127.0.0.1:8081` and joins the external `tnra-shared`
   network. Create that network once with `docker network create tnra-shared`, or remove it from
   `docker-compose.yml` if you don't need it.

## Environment Variables

The following environment variables **MUST** be configured:

| Variable | Description | Required |
|----------|-------------|----------|
| `KEYCLOAK_ISSUER_URI` | Keycloak realm issuer, e.g. `https://auth.example.com/realms/my-realm` | ✅ Yes |
| `KEYCLOAK_CLIENT_ID` | Keycloak client ID (public client, no secret) | ✅ Yes |
| `ASSEMBLYAI_API_KEY` | AssemblyAI API Key | ✅ Yes |
| `JAVA_OPTS` | JVM Options | ❌ No (has default) |

**⚠️ IMPORTANT**: The application will not start without the required environment variables!

`KEYCLOAK_ISSUER_URI` must be the public URL that browsers use, and the container must be able to
reach it. The app checks Keycloak's discovery document at startup, and token issuers must match it
exactly, so an internal hostname such as `http://keycloak:8080` will not work unless Keycloak is
configured with that hostname.

## Accessing the Application

Once the container is running, access the application at:
- **Application**: http://localhost:8080 (`docker run`) or http://localhost:8081 (Compose)
- **Health Check**: `/actuator/health` on the same host and port

Signing in goes through Keycloak, so open the app on a URL registered in the client's redirect URIs.

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
docker compose logs -f mp3-transcriber
```

### Stop the application
```bash
# Docker
docker stop mp3-transcriber-app
docker rm mp3-transcriber-app

# Docker Compose
docker compose down
```

### Access container shell (for debugging)
```bash
# Docker
docker exec -it mp3-transcriber-app sh

# Docker Compose
docker compose exec mp3-transcriber sh
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

- The container runs as root (the image has no `USER`); consider adding a non-root user
- The app is a public OIDC client, so there is no client secret to protect
- Sensitive configuration values should be provided via environment variables
- Consider using Docker secrets or external secret management for production deployments

## Troubleshooting

### Common Issues

1. **Container won't start**: Check logs with `docker logs mp3-transcriber-app`
   - Most common cause: Missing required environment variables
2. **Port already in use**: Change the host port in docker-compose.yml or Docker run command
3. **OAuth2 authentication fails**: Check that `KEYCLOAK_ISSUER_URI` matches the realm's `issuer`
   exactly and that the redirect URI is registered on the Keycloak client
4. **API calls fail**: Verify your AssemblyAI API key
5. **Missing environment variables**: Ensure all required variables are set in your .env file

### Debug Mode

To run with debug logging:
```bash
docker run -d \
  --name mp3-transcriber-app \
  -p 8080:8080 \
  -e KEYCLOAK_ISSUER_URI="https://auth.example.com/realms/my-realm" \
  -e KEYCLOAK_CLIENT_ID="mp3-transcriber" \
  -e ASSEMBLYAI_API_KEY="your-assemblyai-api-key" \
  -e LOGGING_LEVEL_COM_EXAMPLE_TRANSCRIBER=DEBUG \
  mp3-transcriber:latest
``` 