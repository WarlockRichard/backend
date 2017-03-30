#!/usr/bin/env bash

set -xe

if [[ \
-z $DATABASE_USER || \
-z $DATABASE_PASSWORD || \
-z $DATABASE_URL || \
-z $GOOGLE_REDIRECT_URL || \
-z $GOOGLE_CLIENT_ID || \
-z $GOOGLE_CLIENT_SECRET || \
-z $APPLICATION_SECRET \
 ]];
then
echo "ENVIRONMENT VARIABLES ARE UNSET"
exit 1
fi

sbt clean coverage test
sbt coverageReport
sbt flywayMigrate
sbt docker:publishLocal

docker rm -f private-bw-assessment-api || true

docker run -d --name private-bw-assessment-api --restart=always -p 9000:9000 \
	-e DATABASE_USER=${DATABASE_USER} \
    -e DATABASE_PASSWORD=${DATABASE_PASSWORD} \
    -e DATABASE_URL=${DATABASE_URL} \
    -e APPLICATION_SECRET=${APPLICATION_SECRET} \
    -e GOOGLE_REDIRECT_URL=${GOOGLE_REDIRECT_URL} \
    -e GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID} \
    -e GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET} \
    bw-assessment/api:latest
