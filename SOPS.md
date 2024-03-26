## Setup SOPS

### Installation

Install [sops](https://github.com/getsops/sops), by running `brew install sops`

### Plugins

To work with SOPS-files locally you can use either IntelliJ/Rider or Visual Studio Code (vscode)

#### IntelliJ/Rider

If you prefer IntelliJ/Rider you can use install and use the [Simple sops edit](https://plugins.jetbrains.com/plugin/21317-simple-sops-edit) plugin

#### Visual Studio Code (vscode)

NOTE: For vscode it is highly recommended to add a pattern for the tmp files (**/*.ros.tmp.yaml) to your `.gitignore` file, to ensure a decrypted (plaintext) file is never ever committed. This is because vscode plugins opens a temp file when the file is decrypted so there is a risk that this will be checked in to source control if it is not closed (and encrypted) correctly

[SOPS easy edit](https://marketplace.visualstudio.com/items?itemName=ShipitSmarter.sops-edit)
This plugin will look for a `.sops.yaml` file and offer en "Encrypt" button in the right hand corner when it recognize a file with the correct `.ros.yaml` extension

[@signageos/vscode-sops](https://github.com/signageos/vscode-sops)

### Schema validation

#### IntelliJ/Rider

I `IntelliJ/Rider > Settings > Languages & Frameworks > Schemas and DTDs > JSON Schema Mappings` create new schema:

* `Name`: Write name, i.e. `ROS-validation`
* `Schema file or URL:` Put in URL to the latest ROS-schema, [ros_schema_no_v2_1.json](https://kartverket.github.io/ros-as-code/schema/ros_schema_no_v2_1.json)
* `Schema version:` `JSON Schema version 4`
* Add `File path pattern:` `*.ros.yaml`

Now you will get context-help and validation of the content.

#### Visual Studio Code (vscode)

Install one of the following extensions: 

[YAML](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml)

[learn-yaml] https://marketplace.visualstudio.com/items?itemName=docsmsft.docs-yaml

Then open _Command Palette_ (<kbd>⌘ Command</kbd>+<kbd>⇧ Shift</kbd>+<kbd>P</kbd> on Mac) and run `Open User Settings (JSON)`. 
Add the following in the JSON-filen:
```json
{
  "yaml.schemas": {
    "https://kartverket.github.io/ros-as-code/schema/ros_schema_no_v2_1.json": "*ros.yaml",
  },
}
```
Now you will get context-help and validation of the content.


### Setup for a repository

ROS-files should be created in the `.security/ros` in the root directory of the repository and have the suffix `.ros.yaml`.

Create a file called `.sops.yaml` in the `.security/ros`-catalog. This should include the regex pattern that identifies our ROS-files. It should also contain the configuration of the encryption keys used by SOPS. NOTE that the file should have two identical sections defining the keys, with the only difference being the regex pattern that should only be present in the first section. This is to support both vscode-plugins (which needs the regex) as well as the ROS-backend (which does not use the regex):

```
creation_rules:
  - path_regex: \.ros\.yaml$
    shamir_threshold: 2
    key_groups:
      - age:
          - "agexyz......"
      - gcp_kms:
          - resource_id: projects/<project-name>/locations/eur4/keyRings/<keyring-name/cryptoKeys/<key-name>
  # Copy of previous group, without path_regex. This is required.
  - shamir_threshold: 2
    key_groups:
      - age:
          - "agexyz......"
      - gcp_kms:
          - resource_id: projects/<project-name>/locations/eur4/keyRings/<keyring-name>>/cryptoKeys/<key-name>
```

Here we also set the `shamir_threshold` to the value of 2, meaning that we need access to at least one key from each key-group to be able to encrypt and decrypt the files. 

### Encryption key setup

#### age keys

Age keys are asymmetrical so they include a private and a public part. The public part can then be put in the `.sops.yaml` so that files will be encrypted with that public key. The private part of the key can then be used to decrypt the file if you have that stored on your computer (in the `keys.txt` file). 

* Make sure that [age](https://github.com/FiloSottile/age) is installed, i.e. `brew install age`
* If you have a `keys.txt` with the master key, put it in `$HOME/Library/Application Support/sops/age/keys.txt` (on OS X)

#### Generate new master key
* Create a new age key by running `age-keygen -o keys.txt`. Copy the public key, from now on called `<AGE_PUBLIC_KEY>`
* In OS X `keys.txt` must be present in `$HOME/Library/Application Support/sops/age/keys.txt`
* The private key (inside `keys.txt`) should be distributed in a secure way, i.e by 1Password or Dashlane.

#### GCP-keys

GCP-keys are symmetrical, meaning that the same key is used to both encrypt and decrypt content. The key itself is stored in GCP and SOPS connects to GCP and uses the GCP-key when encrypting and decrypting. Access to a GCP-key is goverened by IAM-policies in GCP.

#### Configure GCP KMS
* Make sure Google-CLI, `gcloud` is installed, i.e. `brew install --cask google-cloud-sdk`
* In your favourite terminal run `gcloud auth application-default login`. This will log you in to GCP (via a browser) and save the login information that will be used by the GCP-libraries that SOPS uses.

#### Generate new master key

* Navigate to [GCP KMS](https://console.cloud.google.com/security/kms/keyrings) with a Kartverket-user
* Choose the correct project (or create a new one if yoy can; it is recommended to have keys in separate projects)
* Create "Key ring", call it `ROS` (if you do not have one allready). Choose `Multi-region` and `eur4 (Netherlands and Finland)`
* Create a key, i.e `ROS-as-code`
* Under `Actions` in the key-list, choose `Copy resource name`. That will copy the `<GCP_KEY_ID>` to the clipboard. Den vil se noe slikt ut: `projects/<prosjekt-id>/locations/eur4/keyRings/ROS/cryptoKeys/ROS-as-code`
* Update `./security/.sops.yaml`:

```yaml
creation_rules:
  - path_regex: \.ros\.yaml$
    gcp_kms: <GCP_KEY_ID>
```

#### Access to master keys

Everybody that should update the ROS-files must have access to encrypt/decrypt via the master-key. This can be done achieved in two ways:

* Being in the AD/Entra-group for Team Leads
* **or** by explicit access to the key in [GCP KMS](https://console.cloud.google.com/security/kms/keyrings).

### Rotate SOPS data-key

It is considered good practise to rotate the data key regularely. 

* In your favorite shell, navigate to `.security/ros` in the  repository .
* Kjør `sops -r <name>.ros.yaml`