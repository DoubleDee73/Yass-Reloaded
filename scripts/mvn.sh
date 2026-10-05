#!/usr/bin/env bash
# Wrapper around IntelliJ's bundled Maven with JDK 21, so the whole build
# pipeline is a single allowlistable command. Pass any maven args, e.g.:
#   scripts/mvn.sh -q compile
#   scripts/mvn.sh test -Dtest=YassMicPitchCaptureSpec
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/Users/jchow/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

# Force headless so Swing/AWT tests don't try to reach the macOS window server,
# which is blocked inside the command sandbox and otherwise hangs the test JVM.
# _JAVA_OPTIONS propagates to every JVM, including surefire's forked test VM
# (MAVEN_OPTS would not). Override by exporting YASS_MVN_HEADLESS=false.
if [ "${YASS_MVN_HEADLESS:-true}" = "true" ]; then
  export _JAVA_OPTIONS="${_JAVA_OPTIONS:-} -Djava.awt.headless=true"
fi

MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"

exec "$MVN" "$@"
