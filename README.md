# Disaster Emergency Response Coordination System
### Parallel Programming Project — Java

---

## Project Overview

A Java application that simulates coordinating emergency responses across multiple disaster zones simultaneously. The system uses **parallel programming** to handle many emergencies at the same time, improving response speed and saving lives.

Disasters handled: **Earthquake · Flood · Forest Fire · Landslide · Storm**

---

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│  PRODUCER THREADS (DisasterZone × 5)                    │
│   Zone-Alpha  Zone-Beta  Zone-Gamma  Zone-Delta  ...    │
│        │           │           │          │             │
│        └───────────┴───────────┴──────────┘             │
│                         │                               │
│       PriorityBlockingQueue<Emergency>                  │
│      (highest-severity emergency served first)          │
│                         │                               │
│  CONSUMER THREADS (EmergencyHandler × 4 via pool)       │
│   Handler-1  Handler-2  Handler-3  Handler-4            │
│        │           │           │          │             │
│        └───────────┴───────────┴──────────┘             │
│                         │                               │
│    ResourceManager  (Semaphore per resource type)       │
│  Ambulance  Shelter  FireTeam  FoodWater  RoadCrew      │
│                                                         │
│  MONITOR THREAD (live dashboard — daemon thread)        │
└─────────────────────────────────────────────────────────┘
```

---

## Parallel Programming Concepts Used

| Concept | Where Used | Purpose |
|---|---|---|
| `Thread` | `DisasterZone` | Each zone is an independent producer thread |
| `Runnable` | `EmergencyHandler` | Consumer tasks submitted to thread pool |
| `CountDownLatch` | Main → Zones | All zones start **simultaneously** |
| `PriorityBlockingQueue` | `EmergencyQueue` | Thread-safe, priority-ordered request queue |
| `ExecutorService` | `ResourceDispatcher` | Fixed thread pool managing handler lifecycle |
| `Semaphore` | `ResourceManager` | Limits concurrent access to finite resources |
| `AtomicBoolean/Integer/Long` | Throughout | Lock-free shared counters |
| Daemon Thread | `MonitorThread` | Live dashboard without blocking JVM shutdown |

---

## Project Structure

```
Disaster System/
├── src/
│   ├── DisasterCoordinationSystem.java   ← Main entry point
│   ├── models/
│   │   ├── DisasterType.java             ← Disaster types enum
│   │   ├── ResourceType.java             ← Resource types enum
│   │   └── Emergency.java                ← Emergency data model
│   ├── managers/
│   │   ├── EmergencyQueue.java           ← PriorityBlockingQueue wrapper
│   │   ├── ResourceManager.java          ← Semaphore-based resource pool
│   │   └── StatisticsManager.java        ← AtomicInteger statistics
│   ├── threads/
│   │   ├── DisasterZone.java             ← Producer thread (zone)
│   │   ├── EmergencyHandler.java         ← Consumer Runnable (handler)
│   │   ├── ResourceDispatcher.java       ← ExecutorService thread pool
│   │   └── MonitorThread.java            ← Live dashboard daemon thread
│   └── utils/
│       └── Logger.java                   ← Thread-safe colored logger
├── out/                                  ← Compiled .class files
├── run.sh                                ← One-click compile & run
└── README.md
```

---

## How to Run

### Option 1 — Shell script (easiest)
```bash
cd "Disaster System"
chmod +x run.sh
./run.sh
```

### Option 2 — Manual
```bash
# Compile
javac -d out -sourcepath src src/DisasterCoordinationSystem.java \
  src/models/*.java src/managers/*.java src/threads/*.java src/utils/*.java

# Run
java -cp out DisasterCoordinationSystem
```

---

## How the System Works (Step by Step)

1. **System boots** — `ResourceManager`, `EmergencyQueue`, and `StatisticsManager` are created.
2. **Monitor starts** — A daemon thread begins printing a live dashboard every 4 seconds.
3. **Thread pool starts** — `ExecutorService` creates 4 `EmergencyHandler` worker threads.
4. **Zones initialize** — 5 `DisasterZone` threads start but block on a `CountDownLatch`.
5. **Simultaneous release** — `startLatch.countDown()` releases all 5 zones **at the same instant**, simulating concurrent disaster onset.
6. **Emergencies flood in** — Each zone submits emergencies to the `PriorityBlockingQueue` (highest severity first).
7. **Handlers work in parallel** — 4 handlers concurrently dequeue emergencies, acquire resources via **Semaphore**, respond, then release resources.
8. **Resources contend** — If a resource is exhausted, the requesting thread **blocks** until another handler releases it.
9. **System drains** — Once all zones finish and the queue empties, the dispatcher shuts down gracefully.
10. **Final report** — Monitor prints total emergencies, resolution stats, and resource utilization.

---

## Resources Available

| Resource | Units |
|---|---|
| Ambulance | 8 |
| Shelter | 5 |
| Fire Rescue Team | 6 |
| Food/Water Supply | 10 |
| Road Crew | 4 |

---

## Requirements

- Java 11 or higher (tested with OpenJDK 25)
