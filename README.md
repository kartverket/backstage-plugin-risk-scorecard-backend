# Setup when you have other test resources

An environment file has been provided with all environment variables that are needed to run the application. 
It is recomennded to create a local environment file for secrets and personal configuration. ```.env.local``` is added to the .gitignore.

## Run with intellij
We recommend using IntelliJ for local development. To run the application, simply open the repository locally and select `✨Local Server` as your run configuration, then run it.

Backstage needs to be running, and you need to be logged in for the plugin backend to work. This is because the internal Backstage backend is the issuer of tokens this backend uses as OAuth server.
See [backestage frontend plugin](https://github.com/kartverket/backstage-plugin-risk-scorecard-frontend).

## Run from the terminal
```shell
# set environment variables from env-file
export $(xargs <.env)
export $(xargs <.env.local)

./gradlew bootRun
```

## Run using docker-compose
To build the docker image, run:

```sh
docker image build -t backstage-plugin-risk-scorecard-backend .
```
To run the docker image, run:

```sh
docker-compose run --build local
```

# Setup from scratch: Preconditions and environment variables
## Github App
The Github App is used to issue oauth2 tokens in order to communicate with the Github repositories where the risc analyses are stored.
The permissions that are asked for is set in the GithubHelper. Permissions for the access token requested is these:

```json
{
  "repositories": ["<the repo that the risc belongs to>"],
  "permissions": "contents:write,pull_requests:write,statuses:read"
}
```

### New Github App
Set up a Github App in your organisation and give the following permissions:
Contents: read/write, commit statuses: read/write, pull requests: read/write, and personal access token requests: read/write.

**GITHUB_INSTALLATION_ID**: Locate your application, for example by using this link https://github.com/organizations/<your-org>/settings/installations. Click your application, and find the installation id in the url.

**GITHUB_APP_ID**: When you have created and installed your github app you can locate it here: https://github.com/organizations/<your-org>/settings/apps/<your-app>.

**GITHUB_PRIVATE_KEY_BASE64_ENCODED** is a private key generated for the service, and base64 encoded because formats are hard. Generate a new private key, and base64 encode the PEM-key.

``` shell
echo "<  -----BEGIN RSA PRIVATE KEY----- ... -----END RSA PRIVATE KEY----->" | base64
```

## OAuth Server
We use the backend of the backstage plugin as our issuer/oauth server.

**ISSUER_URI**: In our case the issuer_uri has been the backstage application "https://kv-ros-backstage-245zlcbrnq-lz.a.run.app/api/auth".
For local development the **ISSUER_URI** can be set to "http://localhost:7007/api/auth".

## Json schema and file specific environment variables
**RISC_FOLDER_PATH**: The folder in which the risc analyses are stored, used for read/write of risc and .sops.yaml.s
**FILE_NAME_PREFIX**: Used to find the correct files.
**FILE_NAME_PREFIX**: Used to find the correct files.

## Architecture

### High level components

```mermaid
flowchart TD
     A[Backstage RiSc-plugin] --> B{RiSc-backend/API} -->|.security/risc| C[(GitHub)]
     style B fill:#FFBF00,color:black
```

### Simplified Architecture

![RiSc Architecture](ROS_as_code_arkitektur.png)

### Simplified Git RiSC-flow

```mermaid
%%{init: { 'logLevel': 'debug', 'theme': 'base' } }%%
gitGraph
   commit
   commit
   branch RISC-xyz12
   checkout RISC-xyz12
   commit id: "Added scenarios"
   commit id: "Edited consequence"
   commit id: "Approved by risk owner"
   checkout main
   merge RISC-xyz12
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


# Common errors
## JWT Decoder Bean fails to initialize
This is most likely due to it not being able to get the jwks keys, or the key list might be empty. Try to restart the backstage app.