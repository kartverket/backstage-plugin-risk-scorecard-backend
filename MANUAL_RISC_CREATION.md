# Manual RiSc Creation Guide

This guide explains how to manually create RiSc (Risk Scorecard) files in GitHub without using the UI.

> 🚀 **Want a quick reference?** See [QUICK_REFERENCE.md](docs/QUICK_REFERENCE.md) for a one-page summary.

## Overview

While the recommended way to create RiSc files is through the Backstage UI, you can also create them manually by directly adding files to your GitHub repository. However, you **must follow specific naming conventions** for the system to recognize your RiSc files.

## Naming Convention Requirements

### 1. Branch Name

Create a branch with the following naming pattern:

```
<prefix>-<identifier>
```

**Example:**
```
risc-abc12
```

Where:
- `<prefix>` is configured in your environment (typically `risc`)
- `<identifier>` is a unique 5-character alphanumeric string (e.g., `abc12`, `xyz99`, `a1b2c`)

> 💡 **Note:** The prefix is used for optimization. While the system can technically work with any branch name, using the standard prefix significantly improves performance by reducing GitHub API calls.

### 2. File Path

Place your RiSc YAML file in the following location:

```
<risc-folder>/<branch-name>.<postfix>.yaml
```

**Example:**
```
.security/riscs/risc-abc12.risc.yaml
```

Where:
- `<risc-folder>` is configured in your environment (typically `.security/riscs/`)
- `<branch-name>` must match your branch name exactly (e.g., `risc-abc12`)
- `<postfix>` is configured in your environment (typically `risc`)

### 3. Complete Example

For a RiSc with identifier `abc12`:

| Component | Value |
|-----------|-------|
| Branch name | `risc-abc12` |
| File path | `.security/riscs/risc-abc12.risc.yaml` |
| Full GitHub path | `https://github.com/owner/repo/blob/risc-abc12/.security/riscs/risc-abc12.risc.yaml` |

## Step-by-Step Instructions

### 1. Generate a Unique Identifier

Create a unique 5-character alphanumeric identifier:
- Use a combination of letters (a-z, A-Z) and numbers (0-9)
- Examples: `abc12`, `x7y9z`, `k3m4n`
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

### 4. Add RiSc Content

Edit the file and add your RiSc content following the JSON schema. Here's a minimal example:

```yaml
schemaVersion: "5.0"
title: "My Risk Analysis"
description: "Description of the risk analysis"
objectType: "system"
participants:
  riskOwner: "owner@example.com"
  responsibleManager: "manager@example.com"
# ... additional required fields based on schema version
```

### 5. Commit and Push

```bash
# Add the file
git add .security/riscs/risc-abc12.risc.yaml

# Commit with a descriptive message
git commit -m "Add new RiSc: risc-abc12"

# Push to GitHub
git push origin risc-abc12
```

### 6. Verify in Backstage

The RiSc should now appear in the Backstage UI as a draft. You can:
- View and edit it through the UI
- Create a pull request to publish it
- Continue editing manually if needed

## Common Mistakes to Avoid

### ❌ Branch and File Names Don't Match

```
Branch: risc-abc12
File:   .security/riscs/risc-xyz99.risc.yaml  ❌ WRONG
```

The file name (without path and extension) must match the branch name exactly.

### ❌ Missing or Wrong File Extension

```
.security/riscs/risc-abc12.yaml        ❌ WRONG (missing .risc)
.security/riscs/risc-abc12.risc.yml    ❌ WRONG (should be .yaml not .yml)
.security/riscs/risc-abc12.risc.yaml   ✅ CORRECT
```

### ❌ Wrong Folder Path

```
riscs/risc-abc12.risc.yaml              ❌ WRONG (missing .security/)
.security/risc-abc12.risc.yaml          ❌ WRONG (missing riscs/ folder)
.security/riscs/risc-abc12.risc.yaml    ✅ CORRECT
```

### ❌ Missing Prefix in Branch Name

```
Branch: abc12                           ⚠️ WORKS but not recommended
File:   .security/riscs/abc12.risc.yaml ⚠️ WORKS but not recommended
```

While technically possible, omitting the prefix causes performance issues. Always use the prefix.

## Configuration Reference

The naming convention is controlled by environment variables:

| Variable | Default Value | Description |
|----------|--------------|-------------|
| `FILENAME_PREFIX` | `risc` | Prefix for branch names and file names |
| `FILENAME_POSTFIX` | `risc` | Postfix before the .yaml extension |
| `RISC_FOLDER_PATH` | `.security/riscs` | Folder where RiSc files are stored |

If your deployment uses different values, adjust the examples accordingly.

## Troubleshooting

### RiSc Not Showing in UI

If your manually created RiSc doesn't appear in Backstage:

1. **Check branch name format**: Ensure it follows `<prefix>-<identifier>` (e.g., `risc-abc12`)
2. **Verify file path**: Must be `<folder>/<branch-name>.<postfix>.yaml`
3. **Confirm file exists on branch**: Check that the file exists on the correct branch in GitHub
4. **Wait for sync**: The system may take a moment to detect the new branch
5. **Check logs**: Review backend logs for any errors

### Schema Validation Errors

If the RiSc is detected but shows validation errors:

1. Ensure `schemaVersion` field is present and valid (e.g., `"5.0"`)
2. Verify all required fields are included
3. Check field types match the schema (strings, numbers, arrays, etc.)
4. Refer to the schema documentation for your version

## Need Help?

- Check the [main README](README.md) for more information
- Review [schema changelog](docs/schemaChangelog.md) for schema version details
- Contact your team's Backstage administrator

## Recommended Approach

**For best results, use the Backstage UI** to create RiSc files. Manual creation should only be used when:
- You need to batch-create multiple RiSc files
- You're migrating existing risk analyses
- You're automating RiSc creation through scripts
- The UI is temporarily unavailable

The UI handles all naming conventions automatically and provides helpful validation feedback.

