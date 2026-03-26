# gRPC Chat Application

This is a simple real-time chat application built using gRPC for communication between a server and multiple clients. The project is fully containerized using Docker and managed with Docker Compose.

## Technology Stack

- **Backend**: Kotlin, Spring Boot, gRPC
- **Database**: PostgreSQL
- **Client**: Kotlin, Spring Boot, gRPC
- **Containerization**: Docker, Docker Compose

## Project Structure

- `chat-app-server/`: Contains the Spring Boot application for the gRPC server, which handles chat logic, message broadcasting, and database interactions.
- `chat_app_client/`: Contains the Spring Boot command-line application that acts as the chat client.
- `docker-compose.yml`: Defines the services, networks, and volumes for running the entire application stack (server, database, and clients).
- `Dockerfile`: Each service directory (`chat-app-server` and `chat_app_client`) contains a `Dockerfile` to build its respective Docker image.

## Prerequisites

- [Docker](https://www.docker.com/get-started)
- [Docker Compose](https://docs.docker.com/compose/install/)

## How to Run

The application is designed to be run with Docker Compose, which simplifies the process of starting the server, database, and clients.

### Step 1: Start the Server and Database

First, start the backend services. Open a terminal in the project's root directory and run:

```bash
docker-compose up -d --build chat-server
```

- This command starts the `chat-server` and the `db` (PostgreSQL) services in detached mode (`-d`).
- The `--build` flag ensures the Docker images are built with the latest code.
- The server will be available on port `9090`, and the database on port `5432`.

You can check the status of the running containers with `docker-compose ps`.

### Step 2: Start a Chat Client

To start an interactive chat client, open a **new terminal** in the same root directory and run:

```bash
docker-compose run --rm chat-client
```

- The `run` command starts a new container for the `chat-client` service.
- The `--rm` flag automatically removes the container when you exit the chat session, keeping your system clean.
- You will be prompted to enter a username and a room ID.
- for communication both clients must be joined to same room

### Step 3: Start More Clients

To have multiple users chatting with each other, simply repeat Step 2. Open as many new terminals as you need and run the `docker-compose run --rm chat-client` command in each one. Each terminal will become a separate chat client.

## How to Stop the Application

To stop all running services (server, database, and any running clients started with `up`), run:

```bash
docker-compose down
```

This will stop and remove all containers and networks associated with the project.
