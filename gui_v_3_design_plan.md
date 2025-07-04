# GUI v3 Design – Link Distributor (per‑network budgets)

*Markdown specification for implementation teams*

---

## 1 · Objectives

| ID  | Objective                                                                                   | Rationale                                     |
| --- | ------------------------------------------------------------------------------------------- | --------------------------------------------- |
| G‑1 | Run **all networks in one pass** (NAT + 4 metros + NCB + BUF)                               | Matches updated multi‑network methodology     |
| G‑2 | Provide **per‑network controls** – file, unit‑price, budget, quota, ramp filter, RMSE table | Sampling, cost & capping differ by network    |
| G‑3 | Display **live progress, per‑network cost bars, QC status**                                 | Immediate feedback & troubleshooting          |
| G‑4 | Allow users to **toggle each map layer on/off**                                             | Easier visual inspection of specific networks |
| G‑5 | **Persist** last used paths, numbers & settings                                             | Convenience for repetitive runs               |

---

## 2 · Window Layout (wire‑frame)

```
┌────────────────────────── Link Distributor (GUI v3) ───────────────────────────┐
│ 1 ▸ Input / Price / Budget Table                                               │
│     Net | Shapefile | Price | Budget | Quota | ⚙︎Settings                      │
│     NAT | …         | 2000  | 200 000| 120   | [⚙︎]                           │
│     TA  | …         | 1800  | 180 000| 100   | [⚙︎]                           │
│     …                                                                           │
│                                                                                │
│ 2 ▸ Global Settings (no overall budget)                                         │
│     Survey Buffer  [Browse]    Proximity d = 50 m                               │
│                                                                                │
│ 3 ▸ Output Base Folder  [Browse]                                                │
│                                                                                │
│ 4 ▸ Results Tabs                                                                │
│     Map | Statistics | Costs | Logs                                             │
│                                                                                │
│ Progress 42 %   QC ✓   NAT 96 %  TA 88 %  …   [ Run Analysis ]                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3 · Component Details

### 3.1 Input / Price / Budget Table

*Fixed rows:* **NAT · TA · JLM · HFA · BSH · NCB · BUF**

| Column          | Behaviour                                                            |
| --------------- | -------------------------------------------------------------------- |
| **Shapefile**   | Mandatory for NAT + 4 metros; optional for NCB/BUF. “Browse” dialog. |
| **Price (₪)**   | Editable only for NAT + 4 metros. Numeric ≥ 0.                       |
| **Budget (₪)**  | Per‑network cap; blank ⇒ uncapped. Disabled for NCB/BUF.             |
| **Quota**       | Integer; default from config; user‑editable.                         |
| **⚙︎ Settings** | Opens per‑network dialog (ramp filter, RMSE table, quota, combine).  |

### 3.2 Per‑Network Settings Dialog (⚙︎)

*Ramp Filter* ✓ `DATA1 = 13,14,15`  |  *Combine Two‑Sided* ✓  |  Editable **Road‑Type / RMSE** table.

### 3.3 Global Settings

- Survey Buffer shapefile picker (BUF)
- Proximity distance **d** (0–200 m spinner)

### 3.4 Results TabPane

| Tab            | Key widgets & features                                                         |
| -------------- | ------------------------------------------------------------------------------ |
| **Map**        | Layer tree with check‑boxes → toggle NAT & each metro. Legend auto‑switches.   |
| **Statistics** | Table: per‑network × road‑group → N\_g, n\_g, RMSE, cost.                      |
| **Costs**      | Progress bars for NAT, TA, JLM, HFA, BSH: `cost / budget` (bar red if >100 %). |
| **Logs**       | Scrollable console output.                                                     |

### 3.5 Status Bar

- Progress bar (binds to `Task.updateProgress`).
- QC icon: ✓ or ⚠ (double‑click opens `qc_report.csv`).
- Compact cost summary: `NAT 96 % | TA 88 % | …` (red text when any network >100 %).

---

## 4 · Runtime Flow

```text
Validate inputs → Start background Task
   ↳ Load layers & CRS unify
   ↳ Ramp filter (per network)
   ↳ Edge‑betweenness per network
   ↳ Flagging (BUF, NCB, NAT, DUP)
   ↳ RMSE‑weighted quota sampling
   ↳ Budget trimming per network (maintain group proportions)
   ↳ Write outputs & refresh tabs
   ↳ QC check (internal & external)
End task → update QC icon & cost bars
```

---

## 5 · Development Milestones

| ID       | Depends | Deliverable                                       | Acceptance Test                                   |
| -------- | ------- | ------------------------------------------------- | ------------------------------------------------- |
| **UI‑1** | –       | Multi‑row Input table with price & budget         | Validation blocks run when NAT shapefile missing  |
| **UI‑2** | UI‑1    | Per‑network ⚙︎ dialog (ramp, RMSE, quota)         | Edited RMSE shows in Stats tab after run          |
| **UI‑3** | UI‑2    | Global settings panel (BUF path, distance d)      | Change d alters QC threshold in log               |
| **UI‑4** | UI‑3    | Results TabPane with **layer on/off check‑boxes** | Deselect TA layer → disappears from Map           |
| **UI‑5** | UI‑4    | Costs tab & status bar (per‑network bars)         | Exceed HFA budget → HFA bar red, status msg shown |
| **UI‑6** | UI‑5    | Persist GUI prefs to `~/.linkdist/gui_prefs.json` | Relaunch → previous paths/prices/budgets restored |

---

## 6 · UX Notes

- **All layers ON by default**; user toggles visibility via check‑boxes.
- Cost bar tool‑tips: “`Links selected / Budget links (₪cost)`”.
- On QC failure, QC icon turns red and opens the report on click.
- Numeric cells (price, budget, quota) have inline validation (red border on invalid input).

---

Implementing this design will provide users with a streamlined, per‑network‑budget interface that fully matches the updated methodology while offering clear map controls and real‑time budget feedback.

