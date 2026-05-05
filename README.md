# PartPlan

PartPlan is a program that allows teams to annotate engineering drawings / blueprints with measurement bubbles, and 
record part measurements against their specifications on a table view.

## Features
### Inspection Planning
- Manage Inspection plans that can hold multiple drawings
  - Inspection lots
  - Part records
  - Specifications
- Table view that shows all the measurement data
  - Export the data to CSV or PDF

### Drawing and Annotation
- Can import both images and PDFs
- AI integration that can automatically add measurement bubbles onto the drawing

### Firebase Authentication
- Stores all plans in Firebase storage to allow teams to work on it
- A login system using Firebase authentication for security 
- Settings can easily be modified to store data on other Firebase databases

## Tech Stack
| Layer          | Technology                                      |
|----------------| ----------------------------------------------- |
| UI             | JavaFX                                          |
| Build Tool     | Maven (`pom.xml`)                               |
| Auth + Storage | Firebase                                        |
| AI             | OpenAI API                                      |

## Architecture
- PartPlan uses a mix of the MVVM and MVC architectures.
  - For example: 
    - an annotation bubble is a Model,
    - which then gets data added by the user in a controller (view),
    - then the data gets handled by the respective controller's viewModel.