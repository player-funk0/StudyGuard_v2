#!/bin/sh
# Gradle wrapper shell script
# https://gradle.org/docs/current/userguide/gradle_wrapper.html

# Validate the JVM is available
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
fi

APP_HOME="$(cd "$(dirname "$0")" && pwd)"

exec "$JAVACMD" \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
