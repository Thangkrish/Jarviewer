#!/bin/bash

# Always run from script's directory
cd "$(dirname "$0")" || exit 1

# Check the architecture and use appropriate JavaFX SDK
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
  echo "Using ARM64 JavaFX SDK"
  if [ -d "$PWD/javafx-sdk-arm64" ]; then
    JAVAFX_PATH="$PWD/javafx-sdk-arm64/lib"
  else
    JAVAFX_PATH="$PWD/javafx-sdk-21.0.5/lib"
  fi
else
  echo "Using x86_64 JavaFX SDK"
  JAVAFX_PATH="$PWD/javafx-sdk-21.0.5/lib"
fi

echo "JavaFX path: $JAVAFX_PATH"

# Set the JavaFX native library path for macOS
export DYLD_LIBRARY_PATH="$JAVAFX_PATH"

# Ensure output directory exists
mkdir -p target/classes

# Compile all Java sources
echo "Compiling Java files..."
#javac -cp ".:lib/*:$JAVAFX_PATH/*" -d target/classes $(find src/main/java -name "*.java") || {
#  echo "Compilation failed"; exit 1; }

# Copy resources so CSS and other files are on the classpath
if [ -d src/main/resources ]; then
  cp -R src/main/resources/* target/classes/ 2>/dev/null || true
fi

# Debug port configuration
DEBUG_PORT=5006
DEBUG_MODE="n"  # Set to "y" to enable debug mode

echo "Starting JarViewerFX..."
if [ "$DEBUG_MODE" = "y" ]; then
  echo "Debug mode enabled. Connect your debugger to port $DEBUG_PORT"
  java \
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$DEBUG_PORT \
    --module-path "$JAVAFX_PATH" \
    --add-modules=javafx.controls,javafx.fxml,javafx.graphics,javafx.web,javafx.base \
    -cp "target/classes:lib/*:$JAVAFX_PATH/*" \
    -Djava.library.path="$JAVAFX_PATH" \
    JarViewerFX
else
  java \
    --module-path "$JAVAFX_PATH" \
    --add-modules=javafx.controls,javafx.fxml,javafx.graphics,javafx.web,javafx.base \
    -cp "target/classes:lib/*:$JAVAFX_PATH/*" \
    -Djava.library.path="$JAVAFX_PATH" \
    JarViewerFX
fi
