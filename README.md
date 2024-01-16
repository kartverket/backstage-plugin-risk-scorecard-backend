# kv-ros-backend

### JSON Schema validation

The JSON schema validation is done using the [json-kotlin-schema](https://github.com/pwall567/json-kotlin-schema) library version 0.44.  
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
docker run -it -p 8080:8080 -e SOPS_AGE_PUBLIC_KEY=${SOPS_AGE_PUBLIC_KEY} -e SOPS_AGE_KEY=${SOPS_AGE_PRIVATE_KEY} kv-ros-backend
```