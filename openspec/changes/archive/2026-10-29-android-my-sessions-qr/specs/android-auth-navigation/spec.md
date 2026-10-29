# Delta for Android Auth Navigation

## MODIFIED Requirements

### Requirement: Authenticated home and navigation (AC3)

The authenticated destination MUST show a greeting and a logout action. The navigation drawer MUST list exactly two entries, Home and "Mis clases", and selecting an entry MUST navigate to its destination (`onNavigate`) and close the drawer. It MUST NOT show placeholder or "coming soon" entries. Navigation MUST use Navigation Compose and be extensible with one entry per later section.
(Previously: home showed a greeting and logout only, and the drawer listed only the Home entry with no navigation.)

#### Scenario: Home contents
- GIVEN an authenticated student
- WHEN home is shown
- THEN the greeting and logout are present AND the drawer lists Home and "Mis clases" and no other entry

#### Scenario: Navigate to Mis clases
- GIVEN the drawer open on Home
- WHEN the user selects "Mis clases"
- THEN the My Sessions destination is shown and the drawer closes (manual verification)

#### Scenario: Navigate back to Home
- GIVEN My Sessions shown
- WHEN the user selects Home in the drawer
- THEN Home is shown (manual verification)

#### Scenario: Back from home
- GIVEN home after login
- WHEN the user presses back
- THEN the app does not return to login

#### Scenario: Logout stays available
- GIVEN any authenticated destination
- WHEN the user chooses logout
- THEN the existing logout behaviour applies (manual verification; logic covered by Logout requirement)
