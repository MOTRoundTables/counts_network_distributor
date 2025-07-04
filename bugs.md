# Bugs

## UI-1: NullPointerException in updateCalculations()

**Description:** The application crashes on startup with a `NullPointerException` in `updateCalculations()` because `totalCostLabel` and `totalLinksLabel` are null when the method is called. This occurs because `updateCalculations()` is called in `createHeaderPane()` before `createFooterPane()` (where these labels are initialized) is executed.

**Resolution:** Move the `updateCalculations()` call from `createHeaderPane()` to the `start()` method, after all UI components have been created.