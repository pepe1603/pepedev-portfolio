#!/bin/sh
set -e
if [ -f /etc/portfolio.env ]; then
  set -a
  # shellcheck disable=SC1091
  . /etc/portfolio.env
  set +a
fi
exec java ${JAVA_OPTS:-} -jar /app/app.jar
