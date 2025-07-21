# Schema changelog

## 4.3
- Changes status field in actions to 'ok', 'not ok' and 'not relevant'

## 4.2
- Adds lastUpdated field in actions

## 4.1
- Preset values for probability have been changed to a logarithmic scale with base 20:

| Probability level | Old value | New value      |
|-------------------|-----------|----------------|
| 1                 | 0.01      | 20^-2 = 0.0025 |
| 2                 | 0.1       | 20^-1 = 0.05   |
| 3                 | 1         | 20^0 = 1       |
| 4                 | 50        | 20^1 = 20      |
| 5                 | 300       | 20^2 = 400     |

- Preset values for consequence have been changed to a logarithmic scale with base 20:

| Consequence level | Old value     | New value            |
|-------------------|---------------|----------------------|
| 1                 | 1 000         | 20^3 = 8000          |
| 2                 | 30 000        | 20^4 = 160 000       |
| 3                 | 1 000 000     | 20^5 = 3 200 000     |
| 4                 | 30 000 000    | 20^6 = 64 000 000    |
| 5                 | 1 000 000 000 | 20^7 = 1 280 000 000 |

- Probability and consequence values equal to the old presets are changed to the equivalent new preset values when the schema is migrated to version 4.1. Values differing from the presets are left unchanged.

## 4.0
- Removes deadline and owner from actions
- Removes existing actions from scenarios
- Updated vulnerabilities:
  - User repudiation -> Unmonitored use
  - Compromised admin user -> Unauthorized access
  - Escalation of rights -> Unauthorized access
  - Disclosed secret -> Information leak
  - Denial of service -> Excessive use
  - Introduced "Flawed design"

## 3.3
- Adds url field in actions
- Deadline and owner are now optional in actions

