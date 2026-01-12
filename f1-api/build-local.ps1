$image = "local/f1-api"

Write-Host "Building $image"

./mvnw install "-Dquarkus.container-image.build=true" "-Dquarkus.container-image.image=$image" "-Dquarkus.container-image.push=false"