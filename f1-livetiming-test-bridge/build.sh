#!/bin/sh
echo Building image $IMAGE
echo Push image = $PUSH_IMAGE
./mvnw install -Dquarkus.container-image.build=true -Dquarkus.container-image.image=$IMAGE -Dquarkus.container-image.push=$PUSH_IMAGE -Dcontainer.mainClass="com.kinnovation.Main"