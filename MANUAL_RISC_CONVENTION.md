# Manual RiSc Creation Guide

This guide explains how to manually create RiSc (Risk Scorecard) files in GitHub without using the ROS plugin.

## Overview

While the recommended way to create RiSc files is through the Risc scorecard plugin in kartverket.dev, you can also create them manually by directly adding files to your GitHub repository. However, you **must follow specific naming conventions** for the system to recognize your RiSc files.

## Naming Convention Requirements

### 1. Branch Name

Create a branch with the following naming pattern:

```
<prefix>-<identifier>
```

**Example branch name:**
```
risc-abc12
```

Where:
- `<risc>`= risc
- `<identifier>` is a unique 5-character alphanumeric string (e.g., `abc12`, `xyz99`, `a1b2c`)
- `<identifier>` can also be a suited name for your risc scorecard.

### 2. File Name
The RiSc file must be a YAML file on the following format:

```
<prefix>-<identifier>.<postfix>.yaml
```
Where:
- `<prefix>` = risc
- `<identifier>` = same as in branch name
- `<postfix>` = risc

**Example filename:**
```
risc-abc12.risc.yaml
```
It is important that the filename **matches** the branch name.


### 3. File Path

Place your RiSc YAML file in the following location:

```
<risc-folder>/<identifier>.<postfix>.yaml
```

**Example:**
```
.security/riscs/risc-abc12.risc.yaml
```

Where:
- `<risc-folder>` is configured in your environment (typically `.security/riscs/`)
- `<identifier>` is the **full branch name** (e.g., `risc-abc12`)

### 4. Complete Example

For a RiSc with identifier `abc12`:

| Component | Value                                                                                |
|-----------|--------------------------------------------------------------------------------------|
| Branch name | `risc-abc12`                                                                         |
| File path | `.security/riscs/risc-abc12.risc.yaml`                                               |
| Full GitHub path | `https://github.com/owner/repo/blob/risc-abc12/.security/riscs/risc-abc12.risc.yaml` |

## Step-by-Step Instructions

### 1. Generate a Unique Identifier

- Use a combination of letters (a-z, A-Z) and numbers (0-9)
- Examples: `abc12`, `x7y9z`, `default`, `custom1`
- Ensure it's not already in use in your repository

### 2. Create the Branch

```bash
# From your repository root, create and checkout a new branch
git checkout -b risc-abc12
```

Replace `abc12` with your chosen identifier.

### 3. Create the RiSc File

Create the file at the correct path:

```bash
# Create directory if it doesn't exist
mkdir -p .security/riscs

# Create the RiSc file (replace abc12 with your identifier)
touch .security/riscs/risc-abc12.risc.yaml
```

**Ensure the filename uses the correct format - `<prefix>-<identifier>.risc.yaml`**, and matches the branch name.

### 4. Add RiSc Content

Edit the file and add your RiSc content. If you want your RiSc content to contain the same scenarios and actions as an existing RiSc,
you can copy the encryptet content from an existing risc file.

### 5. Commit and Push

```bash
# Add the file
git add .security/riscs/risc-abc12.risc.yaml

# Commit with a descriptive message
git commit -m "Add new RiSc file with ID: abc12"

# Push to GitHub
git push
```

### 6. Verify in Backstage

The RiSc should now appear in the Risc scorecard plugin. You can:
- View and edit it through the UI
- Create a pull request to publish it
- Continue editing manually if needed

## Common Mistakes to Avoid

### ❌ Missing or Wrong File Extension

```
.security/riscs/risc-abc12.yaml        ❌ WRONG - missing postfix (.risc)
.security/riscs/risc-abc12.risc.yml    ❌ WRONG - should be .yaml not .yml
.security/riscs/abc123.risc.yaml       ❌ WRONG - missing prefix (risc) in filename
.security/riscs/risc-abc12.risc.yaml   ✅ CORRECT
```

### ❌ Wrong Folder Path

```
riscs/risc-abc12.risc.yaml              ❌ WRONG - missing .security/
.security/risc-abc12.risc.yaml          ❌ WRONG - missing riscs/ folder
.security/riscs/<filename>.risc.yaml    ✅ CORRECT
```

### ❌ Missing Prefix in Branch Name

```
Branch: abc12                           ❌ WRONG (missing prefix)
Branch: risc-abc12                      ✅ CORRECT
```

### ❌ Missmatch Between Branch and File Name

```
Branch: risc-abc12, File: risc-xyz99.risc.yaml   ❌ WRONG - identifiers do not match
Branch: risc-abc12, File: risc-abc12.risc.yaml   ✅ CORRECT
```

Always use the prefix for branch name.

## Configuration Reference

The naming convention is controlled by environment variables:

| Variable | Default Value | Description |
|-------|------------|-------------|
| `FILENAME_POSTFIX` | `risc` | Postfix before the .yaml extension |
| `RISC_FOLDER_PATH` | `.security/riscs` | Folder where RiSc files are stored |

## Troubleshooting

### RiSc Not Showing in UI

If your manually created RiSc doesn't appear in the RoS UI:

1. **Check branch name format**: Ensure it follows `<prefix>-<identifier>` (e.g., `risc-abc12`)
2. **Verify file path**: Must be `<folder>/<branch-name>.<postfix>.yaml`
3. **Confirm file exists on branch**: Check that the file exists on the correct branch in GitHub
4. **Wait for sync**: The system may take a moment to detect the new branch
5. **Check logs**: Review backend logs for any errors

## Need Help?

- Check the [main README](README.md) for more information
- Contact the RoS team.