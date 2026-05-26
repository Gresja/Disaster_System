#!/bin/bash
# Compile and run the Disaster Emergency Response Coordination System
# Opens a live web dashboard at http://localhost:8080

export PATH="/usr/local/opt/openjdk@25/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$PROJECT_DIR/src"
OUT="$PROJECT_DIR/out"

echo ""
echo "  Compiling Disaster Emergency Response Coordination System..."
mkdir -p "$OUT"

javac -d "$OUT" \
  "$SRC/models/DisasterType.java" \
  "$SRC/models/ResourceType.java" \
  "$SRC/models/Emergency.java" \
  "$SRC/utils/Logger.java" \
  "$SRC/managers/ResourceManager.java" \
  "$SRC/managers/EmergencyQueue.java" \
  "$SRC/managers/StatisticsManager.java" \
  "$SRC/web/EventBroadcaster.java" \
  "$SRC/web/WebServer.java" \
  "$SRC/threads/DisasterZone.java" \
  "$SRC/threads/EmergencyHandler.java" \
  "$SRC/threads/MonitorThread.java" \
  "$SRC/threads/ResourceDispatcher.java" \
  "$SRC/DisasterCoordinationSystem.java"

if [ $? -ne 0 ]; then
  echo "  Compilation failed."
  exit 1
fi

echo "  Compilation successful."
echo ""
echo "  Dashboard will open at: http://localhost:8080"
echo "  Press Ctrl+C to stop the server."
echo ""

cd "$PROJECT_DIR"
java -cp "$OUT" DisasterCoordinationSystem
