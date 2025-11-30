# Bulk User Import System

A demo application for uploading and processing users from CSV files. The system uses an event-driven architecture with a custom pub/sub mechanism built on Kotlin Coroutines Channels.

## Prerequisites

Before building and running the application, ensure you have the following installed:

**Java 17 or higher**  
**Node.js 16 or higher** 

## Building the Application

### Backend

The backend is built using Gradle.

To build the backend JAR (optional):
```bash
./gradlew build
```

### Frontend

Navigate to the frontend directory and install dependencies:

```bash
cd frontend
npm install
npm build
```

## Running the Application

### Step 1: Start the Backend

From the project root directory:

**Linux/macOS:**
```bash
./gradlew run
```

The backend will start on **http://localhost:8080**

You should see logs indicating:
Ktor server starting
CSV Processing Worker started
Server listening on port 8080

### Step 2: Start the Frontend

Open a new terminal window and navigate to the frontend directory:

```bash
cd frontend
npm start
```

The frontend will start on **http://localhost:3000** and automatically open in your browser.


## Using the Application

1. Open http://localhost:3000 in your browser
2. Click "Choose File" and select a CSV file
3. The CSV should have the following format:
   ```
   id,firstName,lastName,email
   1,Bruce,Lee,bruce.lee@mail.com
   2,Jackie,Chan,jackie.chan@mail.com
   ```
4. Click "Upload" to upload the file
5. The system will process the file asynchronously
6. View the user count and any processing errors in the UI

## API Endpoints

`POST /api/files/upload`  Upload a CSV file
`GET /api/files/users`  Get all processed users
`GET /api/files/users/count`  Get total number of users
`GET /api/files/processing-errors`  Get errors from the last processed file

## Running Tests

To run all tests:
```bash
./gradlew test
```

## Design Choices

### Event-Driven Architecture with Custom EventBus

The application uses an event-driven architecture with a custom pub/sub mechanism built on **Kotlin Coroutines Channels**. This design choice was made for several reasons:

1. Using Kotlin Coroutines Channels directly demonstrates a deep understanding of asynchronous programming, rather than relying on framework magic (like Spring Boot `@Async`).

2. The code explicitly shows:
   - Where coroutines are created (`launch { ... }`)
   - How events flow through the system (`Channel` then `Flow`)
   - How the worker subscribes and processes events

3. Ktor is designed to be lightweight and explicit.

4. While this implementation uses in-memory Channels, the same pattern can be easily extended to use production message brokers (like GCP Pub/Sub, RabbitMQ, or Kafka) by replacing the `EventBus` implementation.

#### How It Works

1. **EventBus** (`infrastructure/messaging/EventBus.kt`):
   - Uses a `Channel<FileUploadedEvent>` with `UNLIMITED` capacity
   - `publish()` is a suspend function that sends events to the channel
   - `subscribe()` returns a `Flow` that can be collected to receive events

2. **Worker** (`application/worker/CsvProcessingWorker.kt`):
   - Subscribes to the EventBus using `eventBus.subscribe()`
   - Uses Kotlin Flow operators (`.onEach`, `.catch`, `.launchIn`) to process events
   - Runs in its own `CoroutineScope` with `SupervisorJob` to prevent worker crashes from stopping the entire application

3. **Application Setup** (`Application.kt`):
   - Creates services explicitly (no dependency injection framework)
   - Launches the worker using `applicationScope.launch { worker.start() }`
   - All coroutine scopes and launches are visible and explicit

#### Why Not Spring Boot?

While Spring Boot provides excellent abstractions, this implementation uses Ktor to:
- Show explicit understanding of coroutines and asynchronous programming
- Avoid "magic" annotations like `@Async` or `@PostConstruct`
- Demonstrate low-level control over concurrency and event processing

The application is designed to handle large CSV files without running out of memory (OOM):
- The CSV is processed row by row using `readNext()`, never loading the entire file into memory
- Users are processed in batches of 10000 to manage memory during processing 
- Only the first 1000 errors are stored to prevent OOM on files with many errors

