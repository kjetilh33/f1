#!/bin/sh
./mvnw install -Dquarkus.container-image.build=true -Dquarkus.container-image.image=$IMAGE -Dquarkus.container-image.push=$PUSH_IMAGE