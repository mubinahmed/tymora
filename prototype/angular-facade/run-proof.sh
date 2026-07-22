#!/usr/bin/env bash
# Compiles the facade + proof against the built webapp and runs the DB-free
# end-to-end proof. Run from prototype/angular-facade/.
set -euo pipefail

W="${UNITIME_WEBINF:-../../target/unitime-4.8/WEB-INF}"
SERVLET_API="${SERVLET_API:-$HOME/.m2/repository/javax/servlet/javax.servlet-api/4.0.1/javax.servlet-api-4.0.1.jar}"

if [ ! -d "$W/classes" ]; then
  echo "Built webapp not found at $W (run 'mvn package' first, or set UNITIME_WEBINF)." >&2
  exit 1
fi

# Windows classpath uses ';'. cygpath converts POSIX paths to Windows form.
CP="$(cygpath -w "$W/classes")"
for j in "$W"/lib/*.jar; do CP="$CP;$(cygpath -w "$j")"; done
CP="$CP;$(cygpath -w "$SERVLET_API")"

# RestRpcServlet now lives in the main source tree; the proof compiles it
# alongside the harness so the two stay in lockstep.
REST_SRC="${REST_SRC:-../../JavaSource/org/unitime/timetable/rest}"

echo "== compiling =="
javac -d out -cp "$CP" \
  "$REST_SRC/RestRpcServlet.java" \
  "$REST_SRC/AsyncRpcExecutor.java" \
  "$REST_SRC/RestServiceServlet.java" \
  "$REST_SRC/PolymorphicTypeAdapterFactory.java" \
  java/proto/FacadeProto.java

echo "== running proof =="
java -cp "$(cygpath -w out);$CP" proto.FacadeProto