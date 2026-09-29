An analysis of your codebase reveals that while the high-level separation of concerns (hub communication, decoding, Kafka forwarding, and state monitoring) is cleanly structured, there are several **critical exception handling gaps, threading pitfalls, and resource management issues** when using the Microsoft SignalR Core Java library (`com.microsoft.signalr:signalr`).

---

### 1. Connection Initialization & Exception Handling in `hubConnection.start()`

In [`F1HubConnection.java`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L213-L216):

```java
hubConnection.start()
        .blockingAwait(15, TimeUnit.SECONDS);
LOG.info("Connected to SignalR hub with connection id {}", hubConnection.getConnectionId());
```

#### Issues:
1. **Ignored `blockingAwait` timeout return value**:
   `Completable.blockingAwait(long timeout, TimeUnit unit)` returns a `boolean`:
    - `true` if it connected within the timeout.
    - `false` if the timeout elapsed.
      Currently, the return value is discarded. If the connection handshake stalls or times out after 15 seconds, execution proceeds directly to `LOG.info` (where `getConnectionId()` is `null`) and then calls `invoke()`, triggering cascade failures.
2. **Uncaught RuntimeExceptions crash the entire application**:
   If DNS lookup fails, the TLS handshake fails, or the server returns an HTTP 4xx/5xx during negotiation, `blockingAwait()` throws a `RuntimeException` (or RxJava `CompositeException`).
   Because [`connectSignalR()`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L181) does not catch this, the exception bubbles up to [`Client.main`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/f1/livetiming/Client.java#L125-L129), which logs `Unrecoverable error` and executes **`System.exit(1)`**. A momentary transient network hiccup at startup permanently kills the service.

#### Best Practice:
- Check the `boolean` return value of `blockingAwait`.
- Wrap connection establishment in a `try-catch` block catching `Exception`.
- Return `false` on failure and let your retry mechanism handle it.

---

### 2. Blocking RxJava Threads Inside `onError` (Deadlock Risk)

In [`F1HubConnection.java`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L231-L235):

```java
@Override
public void onError(Throwable error) {
    LOG.error("Error while subscribing to messages from the hub: " + error.toString());
    close();
}
```

#### Issues:
- Inside [`close()`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L173-L179), you call:
  `hubConnection.stop().blockingAwait(10, TimeUnit.SECONDS);`
- `onError` runs on an internal SignalR / RxJava dispatcher thread. Calling `blockingAwait()` from within an RxJava observer callback blocks the reactive event thread. If tearing down the connection requires dispatching events on that same thread pool, this causes a **thread starvation freeze or deadlock**.
- **Premature state change**: [`setOperationalState(OperationalState.OPEN)`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L237) is executed immediately after `invoke()`, *before* the asynchronous `Single` completes. If the server rejects the subscription, the client was already marked `OPEN`.

#### Best Practice:
Make the initial subscription synchronous with a timeout during `connect()`, so `connect()` only succeeds if subscription succeeds:
```java
try {
    JsonElement response = hubConnection.invoke(JsonElement.class, "Subscribe", List.of(dataStreams))
            .blockingGet(10, TimeUnit.SECONDS);
    onHubResponse(response);
    setOperationalState(OperationalState.OPEN);
    return true;
} catch (Exception e) {
    LOG.error("Failed to subscribe to data streams: {}", e.getMessage(), e);
    safeClose();
    return false;
}
```

---

### 3. Resource Cleanup: `HubConnection.close()` vs `stop()` and Instance Leaks

In [`F1HubConnection.java`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L183-L198):

```java
// Fix for potential leak: close any existing connection before opening a new one
if (hubConnection != null && forceConnect) {
    hubConnection.stop().blockingAwait(10, TimeUnit.SECONDS);
}
// ...
hubConnection = HubConnectionBuilder.create(wssConnect)...build();
```

#### Issues:
1. **`forceConnect` is always `false` on reconnect**:
   When [`asyncKeepAliveLoop`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/f1/livetiming/Client.java#L376) calls `hubConnection.connect()`, `forceConnect` is `false`. Because `operationalState` was set to `CLOSED` by `onClosed`, the check `hubConnection != null && forceConnect` evaluates to `false`. The existing `hubConnection` is overwritten without being stopped or cleaned up!
2. **`HubConnection` implements `AutoCloseable`**:
   `hubConnection.stop()` only terminates the WebSocket transport, but **does not shut down OkHttp's connection pool, dispatcher, or executor threads**. To prevent native socket and thread leaks across reconnects, you must call `hubConnection.close()`.

#### Best Practice:
Always cleanly close any prior instance before creating a new one:
```java
private void cleanupExistingConnection() {
    if (hubConnection != null) {
        try {
            hubConnection.close(); // stops connection AND cleans up OkHttpClient thread pools
        } catch (Exception e) {
            LOG.warn("Error closing previous hub connection: {}", e.getMessage());
        } finally {
            hubConnection = null;
        }
    }
}
```

---

### 4. `onClosed` Callback & Reconnection Strategy

In [`F1HubConnection.java`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L199-L206):

```java
hubConnection.onClosed(exception -> {
    if (exception != null) {
        LOG.warn("The hub closed the connection: {}", exception.getMessage());
    } else {
        LOG.info("Closed the connection to the hub.");
    }
    setOperationalState(F1HubConnection.OperationalState.CLOSED);
});
```

#### Issues:
1. **`exception.getMessage()` can be `null`**: Network drops (e.g. `EOFException`, `SocketTimeoutException`) often have a `null` message. Log the exception directly (`LOG.warn("The hub closed the connection with error", exception);`).
2. **No built-in `withAutomaticReconnect()` in SignalR Java**: Unlike .NET and TypeScript clients, the Java SignalR client does not offer `withAutomaticReconnect()`. Your background poll in [`Client.asyncKeepAliveLoop`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/f1/livetiming/Client.java#L356) works as a fallback, but polling every 5 seconds adds up to 5 seconds of unnecessary latency during a live session.
3. **Cookie Expiration / Sticky Session**: F1 uses AWS ALB (`AWSALBCORS`). If the ALB drops the instance or rebalances, reconnecting with an expired or invalid cookie will fail. Re-negotiating the cookie before reconnecting is necessary, which your code does, but errors in [`getCookie()`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L242) should abort the connection attempt immediately rather than proceeding with an empty cookie.

---

### 5. Defensive Exception Handling in Message Callbacks (`onFeed` & `onHubResponse`)

In [`F1HubConnection.java`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L292):
- If `consumer.accept(record)` or downstream Kafka publishing throws an uncaught runtime exception (e.g. `RecordTooLargeException`, `KafkaException`, or Jackson `NullPointerException`):
    - In `hubConnection.on("feed", ...)`, an uncaught exception escapes into the OkHttp / SignalR frame reader thread, potentially terminating the WebSocket connection.
    - In `onHubResponse` within RxJava's `onSuccess`, throwing an unchecked exception triggers `RxJavaPlugins.onError(UndeliverableException)`.
- **Best Practice**: Wrap the consumer dispatch inside a `try-catch (Throwable t)` block inside [`notifySubscribers()`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L326-L330) and log an error with metrics, ensuring a single bad payload does not kill the stream.

---

### 6. SignalR Builder Timeouts & Keep-Alive Tuning

By default, the SignalR Java client uses:
- Server timeout: 30 seconds
- Handshake timeout: 15 seconds
- Keep-alive ping interval: 15 seconds

You can configure these on [`HttpHubConnectionBuilder`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L195):

```java
hubConnection = HubConnectionBuilder.create(wssConnect)
        .withHeader("Cookie", cookie)
        .withHandshakeResponseTimeout(10_000) // 10s handshake timeout
        .withServerTimeout(45_000)            // 45s before declaring connection dead
        .withKeepAliveInterval(15_000)        // send ping every 15s
        .setHttpClientBuilderCallback(builder -> {
            builder.pingInterval(Duration.ofSeconds(15)); // WebSocket protocol-level ping
        })
        .build();
```

---

### 7. Thread-Safety & Memory Visibility

- In [`F1HubConnection`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java#L61-L65), `hubConnection` and `operationalState` are accessed by the executor thread, main thread, status HTTP server thread, and SignalR callback threads without being declared `volatile`.
- In [`Client`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/f1/livetiming/Client.java#L65-L75), `lastMessageReceived`, `sessionInfo`, and `connectorState` should also be `volatile` (or wrapped in `AtomicReference`).

---

### Recommended Refactoring for `F1HubConnection.java`

Here is how [`F1HubConnection.java`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/signalr/F1HubConnection.java) can be updated to follow best practices:

```java
public final class F1HubConnection implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(F1HubConnection.class);

    private volatile OperationalState operationalState = OperationalState.CLOSED;
    private volatile HubConnection hubConnection = null;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Consumer<LiveTimingRecord> consumer;
    private final boolean messageLogEnabled;

    // ... constructors, metrics, factory methods ...

    public synchronized boolean connect() {
        if (operationalState == OperationalState.OPEN && isConnected()) {
            LOG.warn("connect() - The connection is already open and connected.");
            return true;
        }

        cleanupExistingConnection();

        Optional<String> cookieOpt = getCookie(negotiateUrl);
        if (cookieOpt.isEmpty()) {
            LOG.error("Failed to obtain ALB session cookie from negotiate endpoint.");
            setOperationalState(OperationalState.CLOSED);
            return false;
        }

        try {
            HubConnection connection = HubConnectionBuilder.create(wssConnect)
                    .withHeader("Cookie", cookieOpt.get())
                    .withHandshakeResponseTimeout(15_000)
                    .withServerTimeout(45_000)
                    .withKeepAliveInterval(15_000)
                    .setHttpClientBuilderCallback(builder -> {
                        builder.pingInterval(Duration.ofSeconds(15));
                    })
                    .build();

            connection.onClosed(exception -> {
                if (exception != null) {
                    LOG.warn("SignalR connection closed with error", exception);
                } else {
                    LOG.info("SignalR connection closed normally.");
                }
                setOperationalState(OperationalState.CLOSED);
            });

            connection.<JsonElement, JsonElement, JsonElement>on("feed",
                    this::onFeedSafely,
                    JsonElement.class, JsonElement.class, JsonElement.class);

            // 1. Start connection with timeout verification
            boolean connected = connection.start().blockingAwait(15, TimeUnit.SECONDS);
            if (!connected) {
                LOG.error("Timeout while attempting to start SignalR connection.");
                connection.close();
                setOperationalState(OperationalState.CLOSED);
                return false;
            }

            LOG.info("Connected to SignalR hub with connection ID: {}", connection.getConnectionId());

            // 2. Synchronous Subscribe invocation with timeout
            JsonElement response = connection.invoke(JsonElement.class, "Subscribe", List.of(dataStreams))
                    .blockingGet(15, TimeUnit.SECONDS);

            this.hubConnection = connection;
            setOperationalState(OperationalState.OPEN);
            onHubResponse(response);
            return true;

        } catch (Exception e) {
            LOG.error("Failed to establish SignalR connection or subscribe: {}", e.getMessage(), e);
            cleanupExistingConnection();
            setOperationalState(OperationalState.CLOSED);
            return false;
        }
    }

    private synchronized void cleanupExistingConnection() {
        if (hubConnection != null) {
            try {
                hubConnection.close();
            } catch (Exception e) {
                LOG.warn("Exception closing existing HubConnection: {}", e.getMessage());
            } finally {
                hubConnection = null;
            }
        }
    }

    @Override
    public synchronized void close() {
        setOperationalState(OperationalState.CLOSED);
        cleanupExistingConnection();
    }

    private void onFeedSafely(JsonElement category, JsonElement message, JsonElement timeStamp) {
        try {
            onFeed(category, message, timeStamp);
        } catch (Throwable t) {
            LOG.error("Unhandled error processing feed message: {}", t.getMessage(), t);
        }
    }

    private void notifySubscribers(LiveTimingRecord record) {
        if (consumer != null) {
            try {
                consumer.accept(record);
            } catch (Throwable t) {
                LOG.error("Subscriber threw an exception processing record: {}", t.getMessage(), t);
            }
        }
    }
}
```

### In `Client.java`:
In [`Client.run()`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/f1/livetiming/Client.java#L143), check the boolean return of `hubConnection.connect()`:
```java
boolean connected = hubConnection.connect();
if (!connected) {
    LOG.warn("Initial connection to F1 SignalR hub failed. Background keep-alive loop will retry.");
}
```
This ensures the service stays alive and allows [`asyncKeepAliveLoop()`](file:///c:/dev/kjetilh33/f1/signalr-core-to-kafka/src/main/java/com/kinnovatio/f1/livetiming/Client.java#L356) to re-attempt establishing the connection automatically.
