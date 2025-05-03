# CI/CD with GitHub Actions

This repository uses GitHub Actions for Continuous Integration and Continuous Deployment.

## Workflows

### 1. Build and Test
- Triggered on push to `main` and `develop` branches, and on pull requests to these branches
- Builds the project and runs tests

### 2. Build and Package
- Triggered on push to `main` branch (ignores documentation changes)
- Builds the project, creates a Docker image, and pushes it to GitHub Container Registry
- The image is tagged with the branch name and short commit SHA

### 3. Deploy to Production
- Triggered manually or when a release is published
- Builds and packages the application 
- Deploys to production server

## Required Secrets

For the workflows to function properly, you need to set up the following secrets in your GitHub repository:

- `PROD_HOST`: The hostname or IP address of your production server
- `PROD_USERNAME`: The SSH username for your production server
- `PROD_SSH_KEY`: The SSH private key for connecting to your production server

## Docker Image

The Docker image is built and published to GitHub Container Registry. You can pull it using:

```bash
docker pull ghcr.io/[OWNER]/portal-service:[TAG]
```

## Local Development

For local development, you can use:

```bash
./mvnw clean install
./mvnw spring-boot:run
``` 