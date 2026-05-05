# PartPlan

PartPlan is a JavaFX desktop application for building inspection plans directly from engineering drawings and then using those plans to run inspection lots. It keeps drawing interpretation, revision control, and lot execution in one workflow so inspection data stays tied to the source drawing instead of being split across screenshots, spreadsheets, and disconnected notes.

The application is designed for environments where inspection requirements need to be reviewed visually, approved as controlled revisions, and reused consistently during production inspection. A plan begins as an editable draft, becomes a read-only released revision when complete, and then serves as the basis for inspection lots and recorded measurement results.

> Screenshot placeholder: Insert a screenshot here of the home screen showing the main entry points into inspection plans and inspection lots.
>
> Demonstration placeholder: Insert a link here to the final walkthrough video, presentation, or demonstration recording.

## Table of Contents

- [What PartPlan Solves](#what-partplan-solves)
- [How Work Moves Through the System](#how-work-moves-through-the-system)
- [Accounts, Cloud Storage, and Data Ownership](#accounts-cloud-storage-and-data-ownership)
- [Built With](#built-with)
- [Running the Project](#running-the-project)
- [Local Configuration](#local-configuration)
- [Codebase at a Glance](#codebase-at-a-glance)
- [Team Roles](#team-roles)
- [Operational Notes](#operational-notes)

---

## What PartPlan Solves

Inspection planning usually breaks in predictable places:

- drawing dimensions are copied into external spreadsheets by hand
- revision history becomes difficult to trust once plans start changing
- approved inspection requirements can be edited after release
- measurement results are hard to trace back to the exact drawing revision they came from
- operator comments and lot data become inconsistent across parts and runs

PartPlan addresses that by treating the drawing, the inspection plan, and the inspection lot as one connected process. The result is a clearer path from drawing interpretation to released inspection criteria to lot-level inspection records.

---

## How Work Moves Through the System

### 1. Start with the inspection plan library

Users enter the inspection plan browser to create, open, rename, and review plans before editing them. This keeps plan management separate from the drawing editor and makes the application easier to navigate when multiple plans and revisions exist.

Each plan record shows the information needed to decide what to open next, including revision and release state.

> Screenshot placeholder: Insert a screenshot here of the inspection plan browser with the saved plans table and create/open actions visible.

### 2. Build the plan on top of the drawing

Inside the plan editor, users import drawing pages and place balloons directly on the image. Each balloon stores the data needed for inspection execution, including characteristic, inspection type, nominal value, tolerance limits, and notes. The editor also includes a bubble table so inspection data can be reviewed in a structured format without losing the visual relationship to the drawing.

This is the core authoring space in the application. The drawing remains the focal point, while the supporting fields provide the metadata that turns a balloon into an inspection requirement.

> Screenshot placeholder: Insert a screenshot here of the plan editor with a drawing page loaded, balloons placed, and the supporting inspection fields visible.

### 3. Use AI to accelerate first-pass ballooning

PartPlan includes an OpenAI-backed auto-balloon feature that analyzes a drawing page and creates candidate balloons from detected dimensions, notes, and GD&T-style callouts. The generated balloons are applied as local changes so the user can inspect and correct the result before saving anything.

This feature is intended to reduce repetitive setup work, not replace engineering judgment. Users remain responsible for confirming the accuracy of the generated balloons before the plan is released.

> Screenshot placeholder: Insert a screenshot here of the auto-balloon workflow with generated balloon candidates shown on the drawing.

### 4. Release the plan as a controlled revision

When a draft is ready, the plan can be completed. Completion locks that revision as read-only so it cannot be edited accidentally after release. If the drawing needs to change later, the application creates a new revision instead of modifying the completed plan in place.

That control matters because inspection lots are built from completed plans. The application therefore preserves a clean line between plan authoring and approved inspection use.

> Screenshot placeholder: Insert a screenshot here of a completed plan showing revision information and read-only status in the editor.

### 5. Create lots from approved plans

Inspection lots are created from completed plan revisions, not from drafts. That keeps the lot tied to a stable inspection definition and prevents production data from drifting away from the approved drawing interpretation.

The inspection lot browser provides the same management role for lots that the plan browser provides for plans: users can create, open, rename, delete, and upversion lots from a dedicated selection screen instead of mixing those actions into the editing workspace.

> Screenshot placeholder: Insert a screenshot here of the inspection lot browser with lots linked to completed plans.

### 6. Record measurements and comments during execution

Once a lot is open, users can enter inspection results part by part or work from a broader lot-oriented table, depending on the inspection workflow they prefer. The lot editor also supports comments tied to inspection entries so recorded results can carry context instead of just raw numbers.

This makes PartPlan useful beyond plan creation. It becomes the place where inspection intent and inspection execution meet.

> Screenshot placeholder: Insert a screenshot here of the inspection lot editor, including the part-based view or master table view with comments visible.

---

## Accounts, Cloud Storage, and Data Ownership

### Sign-in and account creation

PartPlan uses Firebase Authentication for access control. Users enter the application through a dedicated sign-in screen and can either sign in with an existing email/password account or create a new account directly from the same page. That keeps onboarding simple while still giving the application a real user boundary instead of acting like a shared anonymous desktop tool.

> Screenshot placeholder: Insert a screenshot here of the sign-in screen showing both the Sign In and Create Account actions.

### User-scoped plan and lot data

Once authenticated, each user works inside their own Firestore-backed data space. Inspection plans, plan revisions, inspection lots, part measurements, and lot comments are all stored under the authenticated user record rather than in one flat shared pool. In practical terms, every plan family and every inspection lot belongs to a user account and stays attached to that account throughout the workflow.

This matters because it preserves ownership, avoids accidental cross-user overlap, and makes the application workable for more than one business at a time. Different organizations, suppliers, or internal teams can use the same deployed system without mixing plan libraries or inspection records.

### Why Firebase fits this project

Firebase gives the project two things that matter immediately:

- a straightforward registration and sign-in experience
- cloud persistence that follows the authenticated user across sessions

That means a released plan is not just saved on one machine. It remains available to the same signed-in user later, along with its revisions, linked inspection lots, saved measurements, and comments. Combined with the read-only completion workflow, this gives PartPlan a controlled and traceable data model instead of a local-file workflow with manual handoff risk.

---

## Built With

| Area | Technology |
|---|---|
| Language | Java 25 |
| Desktop UI | JavaFX 21.0.6 with FXML |
| Build Tool | Apache Maven |
| Persistence | Cloud Firestore |
| Authentication | Firebase Authentication |
| AI Integration | OpenAI Responses API |
| Drawing/PDF Support | Apache PDFBox 3.0.7 |
| JSON Handling | Gson 2.13.2 |
| Testing | JUnit 5 |

---

## Running the Project

### Prerequisites

Before launching the application, make sure the following are available:

- JDK 25
- internet access for Firebase authentication and Firestore persistence
- a Firebase project configured for authentication and Firestore
- an OpenAI API key if auto-ballooning will be used

### Clone the repository

```bash
git clone <repository-url>
cd Capstone
```

### Launch the application

#### Windows

```powershell
.\mvnw.cmd javafx:run
```

#### macOS / Linux

```bash
./mvnw javafx:run
```

### Compile without launching

#### Windows

```powershell
.\mvnw.cmd -q -DskipTests compile
```

#### macOS / Linux

```bash
./mvnw -q -DskipTests compile
```

---

## Local Configuration

### Firebase setup

On first launch, the application opens the Firebase setup screen if a usable project configuration has not been saved yet. After configuration is present, startup flows through the sign-in screen instead.

Typical Firebase setup for this project includes:

- Firebase project ID
- Firestore database selection
- authentication configuration required by the application

### OpenAI setup

Auto-ballooning is optional. To enable it:

1. Open **OpenAI Settings** inside the application.
2. Enter a valid API key.
3. Select the model to use for auto-ballooning.

### Local runtime files

Project-specific runtime data is stored under `.partplan/`. This folder holds local configuration and session data and is intentionally excluded from version control.

---

## Codebase at a Glance

```text
src/main/java/
  app/                 Application wiring, shared menu support, storage paths
  model/               Domain models for plans, pages, balloons, lots, and parts
  service/             Firebase, Firestore, AI, export, PDF, and support services
  view/                JavaFX controllers and navigation helpers
  viewmodel/           Screen state and workflow logic

src/main/resources/
  fxml/                JavaFX layouts
  styles/              Shared stylesheets
  images/              Local image assets

docs/                  Supporting documentation
tools/                 Development utilities
```

---

## Team Roles

Update this section with the final project roster before submission.

| Team Member | Role | Responsibilities | Major Contributions |
|---|---|---|---|
| `<Name>` | `<Role>` | `<Primary responsibilities>` | `<Major features or work areas>` |
| `<Name>` | `<Role>` | `<Primary responsibilities>` | `<Major features or work areas>` |
| `<Name>` | `<Role>` | `<Primary responsibilities>` | `<Major features or work areas>` |
| `<Name>` | `<Role>` | `<Primary responsibilities>` | `<Major features or work areas>` |

---

## Operational Notes

- Completed plans are intentionally read-only.
- Any further changes after completion should be made through a new revision.
- AI-generated balloons should always be reviewed before final save or release.
- Firebase and OpenAI features require valid credentials and network access.
