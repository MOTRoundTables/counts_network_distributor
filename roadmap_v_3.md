# ROADMAP v3 – Link Distributor

*(Tasks ordered: ****GUI first****, then Engine/Methodology)*

---

## 0 · Legend

- **Dep.** → direct dependency (must pass before starting)
- **Deliverable** → tangible artefact or feature
- **Acceptance Test** → objective pass/fail criterion

---

## 1 · GUI Tasks (User‑Facing Layer)

| ID       | Dep. | Task Description                                                                                                       | Deliverable                                     | Acceptance Test                                                              |
| -------- | ---- | ---------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- | ---------------------------------------------------------------------------- |
| **UI‑1** | –    | **Multi‑row Input table** – rows for NAT, TA, JLM, HFA, BSH, NCB, BUF; columns Shapefile • Price • Budget • Quota • ⚙︎ | Table component replaces old single‑file fields | Validation dialog blocks run when mandatory NAT path is missing              |
| **UI‑2** | UI‑1 | **Per‑network Settings dialog** (⚙︎) – ramp filter, Combine Two‑Sided, RMSE table, per‑network quota                   | Modal dialog with editable controls             | Change RMSE for Group1 in TA → value persists & appears in Stats tab         |
| **UI‑3** | UI‑2 | **Global Settings panel** – BUF path picker, distance *d* spinner                                                      | Panel under Input table                         | Change *d* to 75 m → QC threshold in logs updates accordingly                |
| **UI‑4** | UI‑3 | **Results TabPane** – Map (layer on/off check‑boxes), Statistics, Costs, Logs                                          | Fully functional tab set                        | Deselect TA layer → it disappears; Stats & Costs tabs refresh correctly      |
| **UI‑5** | UI‑4 | **Status bar & per‑network cost bars** – live progress, QC icon, cost bars turn red if over budget                     | Status bar component                            | Set HFA budget low → run → HFA cost bar ≥ 100 %; bar turns red, status warns |
| **UI‑6** | UI‑5 | **Preferences persistence** – save/load paths, prices, budgets, quotas to `~/.linkdist/gui_prefs.json`                 | JSON file + auto‑populate on launch             | Relaunch app → previous values automatically filled                          |

---

## 2 · Engine & Methodology Tasks

| ID       | Dep. | Task Description                                                                                                | Deliverable                      | Acceptance Test                                                                     |
| -------- | ---- | --------------------------------------------------------------------------------------------------------------- | -------------------------------- | ----------------------------------------------------------------------------------- |
| **E‑1**  | UI‑3 | **Layer Loader & CRS Harmoniser** – load 6 shapefiles, re‑project to common CRS; add flag fields                | In‑memory FeatureCollections     | JUnit: CRS of all layers equals target; links contain four flag attributes          |
| **E‑2**  | E‑1  | **Ramp Filter (optional)** – per‑network DATA1 filter from GUI                                                  | Filtered feature sets            | TA ramps excluded from graph; `CENT_NET = 0` present on ramp features               |
| **E‑3**  | E‑2  | **Edge‑Betweenness Centrality (per network)**                                                                   | `CENT_NET` attribute on links    | Scores 0‑1; NAT & metros processed independently                                    |
| **E‑4**  | E‑3  | **Pre‑flagging** – BUF (FLAG\_HH), NCB (FLAG\_NCB), NAT×Metro (FLAG\_NAT), Metro×Metro (FLAG\_DUP by CENT\_NET) | Updated FeatureCollections       | Random overlap TA×NAT sets FLAG\_NAT=1; Metro×Metro keeps higher centrality segment |
| **E‑5**  | E‑4  | **Road‑type / RMSE sample‑size calculation**                                                                    | `n_g` per group per network      | Sum n\_g equals network quota; proportions match RMSE weights                       |
| **E‑6**  | E‑5  | **Quota‑based selection** – choose top‑`n_g` links per group                                                    | Quota sample lists               | Selected counts per group = n\_g                                                    |
| **E‑7**  | E‑6  | **Per‑network Budget Trimming** – cap link count to (Budget / Unit‑Price) while preserving RMSE proportions     | Budget‑compliant sample          | Σ(cost\_net) ≤ Budget; ≥ 60 % of original quota retained                            |
| **E‑8**  | E‑7  | **Output writer** – folder hierarchy, `centrality.shp`, `results.csv`, `sample_budget.shp` (if trimmed)         | Files on disk                    | Files exist; CRS correct; attribute counts match selection                          |
| **E‑9**  | E‑8  | **Quality Control module** – external & internal overlap checks                                                 | `qc_report.csv` + pass/fail      | Run on sample ⇒ 0 violations; GUI QC icon ✓                                         |
| **E‑10** | E‑8  | **Cost report & summary CSV**                                                                                   | `cost_report.csv`, `summary.csv` | Costs per network correct; budgets not exceeded                                     |

---

## 3 · Delivery Order

```
UI‑1 → UI‑2 → UI‑3 → UI‑4 → UI‑5 → UI‑6
                     ↓
                 E‑1 → E‑2 → E‑3 → E‑4 → E‑5 → E‑6 → E‑7 → E‑8 → E‑9 → E‑10
```

*All tasks must pass their acceptance tests before proceeding to the next dependency.*

---

## 4 · Notes & Assumptions

- **Budgets** apply **per priced network** (NAT + 4 metros). There is *no* global cap field.
- NCB & BUF layers are cost‑free and appear only in flagging/QC.
- Map legend automatically reflects the active layer’s centrality range.
- The RMSE table supports up to 7 road‑type groups; groups can be added/removed in the dialog.
- Quota defaults, RMSE defaults and default budgets/ prices ship in `default_config.yaml`.

Implementing this roadmap (GUI then Engine) will deliver a fully functional application aligned with the latest methodology and user requirements.

