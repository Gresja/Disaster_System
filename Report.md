# Disaster Emergency Response Coordination System
## Project Report — Parallel Programming Course

---

## 1. Introduction

### 1.1 Problem Statement

When natural disasters occur — earthquakes, floods, forest fires, landslides, or storms — emergency centers receive many requests simultaneously. Injured people need ambulances, families need shelters, fire zones need rescue teams, roads may be blocked, and food/water supplies must be delivered.

If these requests are handled **sequentially** (one at a time), the response is dangerously slow. People die waiting in line while resources sit idle.

### 1.2 Proposed Solution

This project implements a **Disaster Emergency Response Coordination System** that uses **parallel programming** in Java to process multiple emergencies simultaneously. The system simulates:

- **5 disaster zones** reporting emergencies at the same time (parallel producers)
- **4 response handlers** working on different emergencies simultaneously (parallel consumers)
- **Limited shared resources** (ambulances, shelters, etc.) that multiple handlers compete for safely
- A **real-time web dashboard** to visualize the entire process

### 1.3 Technologies Used

| Technology | Purpose |
|---|---|
| Java 11+ | Core programming language |
| java.util.concurrent | Thread, Semaphore, ExecutorService, BlockingQueue, Atomic types |
| com.sun.net.httpserver | Built-in HTTP server (no external dependencies) |
| Server-Sent Events (SSE) | Real-time push from Java to browser |
| HTML/CSS/JavaScript | Web dashboard |

---

## 2. System Architecture

### 2.1 Architecture Diagram

```
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  PRODUCER THREADS (DisasterZone × 5)                       │
│                                                            │
│   Zone-Alpha    Zone-Beta    Zone-Gamma                    │
│   (Earthquake)  (Flood)      (Forest Fire)                 │
│        │            │             │                        │
│   Zone-Delta    Zone-Epsilon                               │
│   (Landslide)   (Storm)                                    │
│        │            │                                      │
│        └────────────┴─────────────┘                        │
│                     │                                      │
│         ┌───────────▼────────────┐                         │
│         │ PriorityBlockingQueue  │                         │
│         │ (sorted by severity)   │                         │
│         └───────────┬────────────┘                         │
│                     │                                      │
│  CONSUMER THREADS (EmergencyHandler × 4)                   │
│                                                            │
│   Handler-1    Handler-2    Handler-3    Handler-4          │
│        │            │            │           │             │
│        └────────────┴────────────┴───────────┘             │
│                     │                                      │
│         ┌───────────▼────────────┐                         │
│         │   ResourceManager      │                         │
│         │   (Semaphore per type)  │                        │
│         │                        │                         │
│         │  🚑 Ambulance (8)      │                         │
│         │  🏠 Shelter (5)        │                         │
│         │  🚒 Fire Rescue (6)    │                         │
│         │  🥫 Food/Water (10)    │                         │
│         │  🚧 Road Crew (4)      │                         │
│         └────────────────────────┘                         │
│                                                            │
│  BACKGROUND THREADS                                        │
│   MonitorThread (daemon) — prints status + pushes to web   │
│   WebServer (daemon) — serves dashboard on port 8080       │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 2.2 Design Pattern: Producer-Consumer

The system follows the classic **Producer-Consumer** pattern:

- **Producers** = 5 DisasterZone threads that generate emergencies
- **Shared Buffer** = PriorityBlockingQueue that holds pending emergencies
- **Consumers** = 4 EmergencyHandler threads (in a thread pool) that process emergencies

This pattern ensures that producers and consumers work independently and at their own speed, without data corruption or race conditions.

---

## 3. Parallel Programming Concepts Used

### 3.1 Thread (DisasterZone.java)

Each disaster zone is an independent `Thread`. All 5 zones run simultaneously, producing emergencies at random intervals.

```java
public class DisasterZone extends Thread {
    @Override
    public void run() {
        startSignal.await();   // wait for "go" signal
        for (int i = 0; i < emergenciesCount; i++) {
            Emergency emergency = new Emergency(zoneName, type, severity);
            emergencyQueue.submit(emergency);
            Thread.sleep(500 + random.nextInt(1000));  // random delay
        }
    }
}
```

**Why needed:** In a real disaster, multiple areas report emergencies at the same time. Each zone must operate independently — one zone shouldn't wait for another to finish.

### 3.2 CountDownLatch (DisasterCoordinationSystem.java)

A `CountDownLatch(1)` ensures all 5 zone threads start at the **exact same moment**, simulating a simultaneous disaster onset.

```java
CountDownLatch startLatch = new CountDownLatch(1);
// ... create all zone threads (they call startSignal.await()) ...
startLatch.countDown();  // releases ALL zones at once
```

**Why needed:** Without this, zones would start at slightly different times depending on thread scheduling. The latch guarantees true simultaneous start.

### 3.3 PriorityBlockingQueue (EmergencyQueue.java)

The emergency queue is a `PriorityBlockingQueue<Emergency>` that:

- **Sorts by severity** — most severe emergencies are handled first (Emergency implements Comparable)
- **Is thread-safe** — multiple zones can `put()` and multiple handlers can `take()` simultaneously
- **Blocks on empty** — handlers call `take()` which automatically waits if the queue is empty

```java
private final PriorityBlockingQueue<Emergency> queue = new PriorityBlockingQueue<>();

public void submit(Emergency emergency) {
    queue.put(emergency);       // never blocks (unbounded)
}

public Emergency take() throws InterruptedException {
    return queue.take();        // blocks if empty
}
```

**Why needed:** Without a thread-safe queue, multiple threads writing/reading simultaneously would cause data corruption. The blocking behavior also prevents busy-waiting.

### 3.4 ExecutorService / Thread Pool (ResourceDispatcher.java)

Instead of manually creating handler threads, we use `Executors.newFixedThreadPool(4)` which creates a pool of 4 reusable worker threads.

```java
this.threadPool = Executors.newFixedThreadPool(handlerCount);
for (EmergencyHandler handler : handlers) {
    threadPool.submit(handler);   // each handler runs as a Runnable
}
```

**Why needed:** Thread pools are more efficient than creating/destroying threads per task. The pool manages thread lifecycle, reuse, and shutdown.

### 3.5 Semaphore (ResourceManager.java)

Each resource type has its own **counting Semaphore** that controls concurrent access:

```java
semaphores.put(ResourceType.AMBULANCE, new Semaphore(8, true));  // 8 permits, fair
semaphores.put(ResourceType.ROAD_CREW, new Semaphore(4, true));  // 4 permits, fair
```

When a handler needs a resource:
- `semaphore.acquire()` — takes 1 permit. If none left, the thread **blocks** until one is released.
- `semaphore.release()` — returns 1 permit, waking up a blocked thread.

```java
public boolean acquire(ResourceType type, int emergencyId) throws InterruptedException {
    semaphores.get(type).acquire();   // blocks if count == 0
    return true;
}

public void release(ResourceType type, int emergencyId) {
    semaphores.get(type).release();   // wakes up waiting thread
}
```

**Why needed:** There are only 4 road crews, but multiple handlers might need one at the same time. The Semaphore prevents over-allocation and makes threads wait fairly.

### 3.6 Atomic Variables (throughout)

`AtomicInteger` and `AtomicLong` provide lock-free thread-safe counters:

```java
private final AtomicInteger totalReceived = new AtomicInteger(0);
totalReceived.incrementAndGet();  // thread-safe increment
```

Used in: `EmergencyQueue` (received/resolved counters), `ResourceManager` (usage stats), `StatisticsManager` (resolution counts, total response time).

**Why needed:** Multiple threads update these counters simultaneously. Normal `int++` would cause race conditions.

### 3.7 AtomicBoolean (EmergencyHandler.java)

A shared `AtomicBoolean running` serves as a cooperative shutdown signal. All 4 handlers check it every loop iteration:

```java
while (running.get() || emergencyQueue.getQueueSize() > 0) {
    Emergency emergency = emergencyQueue.take();
    handleEmergency(emergency);
}
```

**Why needed:** The main thread sets `running.set(false)` to tell all handlers to stop. AtomicBoolean ensures this change is visible to all threads immediately (volatile semantics).

### 3.8 Daemon Thread (MonitorThread.java)

The monitor thread is marked as `setDaemon(true)`. It runs in the background, printing system status every 4 seconds and pushing updates to the web dashboard.

```java
public MonitorThread(...) {
    super("Monitor-Thread");
    setDaemon(true);   // won't prevent JVM exit
}
```

**Why needed:** Daemon threads are automatically terminated when all non-daemon threads finish. This prevents the monitor from keeping the program alive indefinitely.

### 3.9 Synchronized Logger (Logger.java)

All logging methods are `synchronized` to prevent garbled output when multiple threads print at the same time:

```java
public static synchronized void info(String source, String message) {
    System.out.printf("[%s] [%s] %s%n", timestamp(), source, message);
}
```

**Why needed:** Without synchronization, concurrent `System.out.println()` calls from different threads would interleave characters, producing unreadable output.

---

## 4. Resource Requirements per Disaster Type

Each disaster type requires a specific set of resources. When a handler picks up an emergency, it must acquire ALL required resources before it can respond:

| Disaster Type | Required Resources |
|---|---|
| 🌋 Earthquake | 🚑 Ambulance + 🏠 Shelter + 🚧 Road Crew |
| 🌊 Flood | 🥫 Food/Water Supply + 🏠 Shelter + 🚧 Road Crew |
| 🔥 Forest Fire | 🚒 Fire Rescue Team + 🚑 Ambulance |
| ⛰️ Landslide | 🚧 Road Crew + 🚑 Ambulance + 🥫 Food/Water Supply |
| ⛈️ Storm | 🏠 Shelter + 🥫 Food/Water Supply + 🚑 Ambulance |

### Available Resource Pool

| Resource | Total Units |
|---|---|
| 🚑 Ambulance | 8 |
| 🏠 Shelter | 5 |
| 🚒 Fire Rescue Team | 6 |
| 🥫 Food/Water Supply | 10 |
| 🚧 Road Crew | 4 |

**Key observation:** Road Crew has only 4 units but is needed by 3 disaster types (Earthquake, Flood, Landslide). This creates **resource contention** — handlers must wait when all road crews are deployed, demonstrating the Semaphore blocking behavior.

---

## 5. Project Structure

```
Disaster System/
├── src/
│   ├── DisasterCoordinationSystem.java    ← Main entry point
│   ├── models/
│   │   ├── DisasterType.java              ← Enum: 5 disaster types
│   │   ├── ResourceType.java             ← Enum: 5 resource types with capacities
│   │   └── Emergency.java                ← Emergency model (Comparable by severity)
│   ├── managers/
│   │   ├── EmergencyQueue.java           ← PriorityBlockingQueue wrapper
│   │   ├── ResourceManager.java          ← Semaphore-based resource pool
│   │   └── StatisticsManager.java        ← Atomic counters for statistics
│   ├── threads/
│   │   ├── DisasterZone.java             ← Producer thread (1 per zone)
│   │   ├── EmergencyHandler.java         ← Consumer Runnable (4 in pool)
│   │   ├── ResourceDispatcher.java       ← ExecutorService thread pool manager
│   │   └── MonitorThread.java            ← Background dashboard updater
│   ├── utils/
│   │   └── Logger.java                   ← Synchronized colored console logger
│   └── web/
│       ├── EventBroadcaster.java         ← SSE event broadcaster (Singleton)
│       └── WebServer.java                ← Built-in HTTP server
├── web/
│   └── index.html                        ← Real-time browser dashboard
├── out/                                  ← Compiled .class files
├── run.sh                                ← Compile and run script
├── README.md                             ← Quick-start guide
└── Report.md                             ← This report
```

**Total: 14 Java source files + 1 HTML dashboard**

---

## 6. Execution Flow (Step by Step)

### Step 1: System Initialization
The main method creates the shared infrastructure:
- `EmergencyQueue` — the shared priority queue
- `ResourceManager` — initializes Semaphores for all resource types
- `StatisticsManager` — atomic counters for response statistics
- `WebServer` — starts HTTP server on port 8080

### Step 2: Start Background Threads
- `MonitorThread` starts as a daemon, printing status every 4 seconds
- `ResourceDispatcher` creates a thread pool with 4 `EmergencyHandler` workers

### Step 3: Create Zone Threads
5 `DisasterZone` threads are created and started. Each one immediately blocks on `startSignal.await()`, waiting for the go signal.

### Step 4: Simultaneous Release
`startLatch.countDown()` is called — all 5 zone threads unblock simultaneously and begin generating emergencies.

### Step 5: Parallel Processing
- **Zones** produce emergencies at random intervals (500-1500ms) and add them to the shared queue
- **Handlers** pull the highest-severity emergency from the queue, acquire resources via Semaphore, simulate response, release resources, and loop
- **Monitor** periodically reads shared state and updates both console and web dashboard

### Step 6: Resource Contention
When a resource runs out (e.g., all 4 Road Crews are deployed), the handler that needs one **blocks** on `semaphore.acquire()`. It automatically resumes when another handler calls `semaphore.release()`.

### Step 7: Shutdown
Once all zones finish and the queue is drained:
1. `systemRunning.set(false)` signals handlers to stop
2. `dispatcher.shutdown()` calls `threadPool.shutdown()` and waits
3. Final statistics are printed
4. Web dashboard shows completion banner

---

## 7. Web Dashboard

The system includes a real-time web dashboard accessible at `http://localhost:8080`. It uses **Server-Sent Events (SSE)** — a persistent HTTP connection where the Java server pushes JSON events to the browser as they happen.

### Dashboard Components

| Section | What it shows |
|---|---|
| **Flow Diagram** | 4-step pipeline: Disasters → Queue → Handlers → Resolved |
| **Disaster Zones** | Status of each zone (Waiting / Active / Done) with emergency count |
| **Resource Bars** | Real-time utilization of each resource type (green/yellow/red) |
| **Resource Requirements Table** | What each disaster type needs from the resource pool |
| **Event Log** | Chronological feed: REPORTED → HANDLING → RESOLVED for each emergency |

### SSE Architecture

```
Java Server                          Browser
    │                                    │
    │  GET /events                       │
    │◄───────────────────────────────────│
    │                                    │
    │  Content-Type: text/event-stream   │
    │───────────────────────────────────►│
    │                                    │
    │  data: {"type":"emergency_submitted"...}
    │───────────────────────────────────►│  → onEvent() updates DOM
    │                                    │
    │  data: {"type":"resource_acquired"...}
    │───────────────────────────────────►│  → updateResource() moves bar
    │                                    │
    │  data: {"type":"emergency_resolved"...}
    │───────────────────────────────────►│  → counter++ animation
    │         ... continuous stream ...  │
```

---

## 8. Thread Safety Summary

| Shared Resource | Protection Mechanism | Why |
|---|---|---|
| Emergency queue | PriorityBlockingQueue | Multiple producers + consumers access simultaneously |
| Resources (ambulances, etc.) | Semaphore (per type) | Limits concurrent access to finite resources |
| Received/Resolved counters | AtomicInteger | Lock-free increment by many threads |
| Response time accumulator | AtomicLong | Lock-free addition by many threads |
| System running flag | AtomicBoolean | Visible to all threads immediately |
| Console output | synchronized methods | Prevents interleaved printing |
| SSE broadcast | synchronized + CopyOnWriteArrayList | Multiple threads emit events simultaneously |

---

## 9. How to Run

### Prerequisites
- Java 11 or higher installed

### Steps
```bash
cd "Disaster System"
chmod +x run.sh
./run.sh
```

The script compiles all source files and runs the system. The browser opens automatically to `http://localhost:8080`.

Press `Ctrl+C` to stop.

---

## 10. Conclusion

This project demonstrates key parallel programming concepts through a realistic disaster response simulation:

1. **Multiple threads** running simultaneously (zones + handlers + monitor)
2. **Thread-safe data sharing** using `PriorityBlockingQueue`, `Semaphore`, and `Atomic` types
3. **Synchronization** with `CountDownLatch` (simultaneous start) and `synchronized` (logging)
4. **Thread pooling** with `ExecutorService` for efficient thread management
5. **Resource contention** where threads must wait for limited shared resources
6. **Graceful shutdown** using cooperative `AtomicBoolean` signaling

The system processes 12 emergencies from 5 zones using 4 parallel handlers, achieving significantly faster response times than a sequential approach would allow. The web dashboard makes the parallelism visible in real time, showing how multiple threads interact, compete for resources, and resolve emergencies simultaneously.

---

*Disaster Emergency Response Coordination System — Parallel Programming Course Project*
