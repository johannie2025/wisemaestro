#!/bin/sh
#
# Gradle start up script for UN*X
#

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Increase the maximum file descriptors if we can.
MAX_FD="maximum"
case "$(uname -s)" in
    Linux*|Darwin*)
        MAX_FD_LIMIT=$(ulimit -H -n 2>/dev/null || echo "")
        if [ -n "$MAX_FD_LIMIT" ]; then
            ulimit -n "$MAX_FD_LIMIT" 2>/dev/null || true
        fi
    ;;
esac

# Collect all arguments for the java command
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
