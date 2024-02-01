# kv-ros-backend
## High level components

```mermaid
flowchart TD
     A[Backstage ROS-plugin] --> B{ROS-backend/API} -->|.sikkerhet/ros| C[(GitHub)]
     style B fill:#FFBF00,color:black
```

## Simplified Git ROS-flow

```mermaid
gitGraph TB:
   commit
   commit
   branch ROS-xyz12
   checkout ROS-xyz12
   commit id: "Lagt til scenarier"
   commit id: "Justert konsekvens"
   commit id: "Godkjent" tag: "Godkjent av risikoeier"
   checkout main
   merge ROS-xyz12
   commit
   commit
```

### JSON Schema validation

The JSON schema validation is done using the [json-kotlin-schema](https://github.com/pwall567/json-kotlin-schema)
library version 0.44.  
This library has some limitations.  
It does not fully support the latest JSON Schema draft.  
It covers our need regarding the JSON Schema validation.  
If the version of the schema is updated, ensure that the library supports it.

### Docker

To build the docker image, run:

```sh
docker image build -t kv-ros-backend .
```

To run the docker image, run:

```sh
docker run -it -p 8080:8080 -e GCP_KMS_RESOURCE_PATH=${GCP_KMS_RESOURCE_PATH} -e SOPS_AGE_PUBLIC_KEY=${SOPS_AGE_PUBLIC_KEY} -e SOPS_AGE_KEY=${SOPS_AGE_PRIVATE_KEY} kv-ros-backend
```

Eller bruk .run-config

```
📦 Containerized Server
```

### Kjør lokalt

Kan kjøres opp med .run-config.
Husk å sette miljøvariabel: ```SOPS_AGE_KEY``` 🪄

```
✨ Lokal Server
```

