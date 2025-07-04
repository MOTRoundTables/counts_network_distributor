# Methodology Documentation – Multi‑Network Traffic‑Count Sampling

This document explains, step by step, the sampling workflow for a national road network and four metropolitan networks (Tel‑Aviv, Jerusalem, Haifa, Beer‑Sheva). It also handles two auxiliary layers (National Count‑Basket, Survey Buffer) and includes an optional budget constraint.

---

## 1. Data Loading & Harmonisation

1. **Input layers**
   - National (NAT)
   - Tel‑Aviv (TA)
   - Jerusalem (JLM)
   - Haifa (HFA)
   - Beer‑Sheva (BSH)
   - National Count‑Basket (NCB)
   - Survey Buffer (BUF – “Haitz & Hagora”)
2. **Common CRS** – re‑project every layer to a single CRS.
3. **Flag fields** – add four Boolean attributes (default 0):\
   `FLAG_HH` | `FLAG_NCB` | `FLAG_NAT` | `FLAG_DUP`.

---

## 2. Ramp Filtering (Optional)

- In the GUI, tick **“Filter Ramps”** and list `DATA1` codes (e.g. 13,14,15).
- These links are excluded **only** from the centrality graph; they stay in the layer with `CENT_NET = 0`.

---

## 3. Network‑Specific Centrality

- Run **edge‑betweenness** separately on:
  1. NAT (ramps removed)
  2. TA (ramps removed)
  3. JLM (ramps removed)
  4. HFA (ramps removed)
  5. BSH (ramps removed)
- Write the 0–1 normalised score to `CENT_NET`.

---

## 4. Pre‑Flagging – Link‑to‑Link Overlap Tests

| Step | Spatial test  | Flag set                                                                            | Purpose                                        |
| ---- | ------------- | ----------------------------------------------------------------------------------- | ---------------------------------------------- |
| A    | Link ∩ BUF    | `FLAG_HH = 1`                                                                       | Exclude areas already covered by survey buffer |
| B    | Link ∩ NCB    | `FLAG_NCB = 1`                                                                      | Exclude overlaps with National Count‑Basket    |
| C    | Metro ∩ NAT   | `FLAG_NAT = 1` (metro side)                                                         | National network has precedence                |
| D    | Metro ∩ Metro | Keep link with higher `CENT_NET`; others `FLAG_DUP = 1`. Tie → TA > JLM > HFA > BSH | Remove inter‑metro duplicates                  |

> **Implementation tip:** build an `STRtree`; query candidates with an expanded envelope (`Envelope.expandBy(d)`).

---

## 5. Candidate Lists

- **NAT** – drop links flagged `FLAG_HH` **or** `FLAG_NCB`.
- **Metros** – drop links with **any** flag set.

---

## 6. Sample Size by Road‑Type & RMSE

1. **Assign link to road‑type group** (Group1 – Group6 or Other) via its `TYPE` attribute.
2. **Count eligible links (N\_g)** per group *after* flag filtering.
3. **Weight per group** `w_g = 1 / RMSE_g²`; RMSE values are provided in the GUI (e.g. `Group1:0.15`, `Group2:0.20`, …).
4. **Compute sample size**\
   `n_g = (N_g × w_g) / Σ w_g`  → round to nearest integer.
5. **Rank links inside each group** by descending `CENT_NET`.
6. **Select the top ****\`\`**** links**. If not enough remain, record the deficit.

Result: a **road‑type quota sample** balanced by statistical error (RMSE) and by centrality.

---

## 7. Budget Constraint (Optional)

> **Cost is considered only for the five priced networks** – **NAT, TA, JLM, HFA, BSH**.\
> Every other layer (NCB, BUF) has no unit‑price and never affects the budget.

### 7.1 Configuration

You may specify **either** a single global budget or a dedicated budget per network:

```yaml
# unit prices (ILS per counted link)
prices:
  NAT: 2000
  TA:  1800
  JLM: 1600
  HFA: 1400
  BSH: 1200
# ONE of the following two lines:
max_budget: 850000         # global cap for all five networks
# budgets:
#   NAT: 200000
#   TA:  180000
#   ...                    # per‑network caps (comment out if unused)
```

If both `budgets:` and `max_budget` are present, **per‑network caps win** and the global cap is ignored.

### 7.2 Cost Calculation

For each priced network:

```
cost_net = unit_price[net] × selected_links[net]
```

*Full‑sample cost* = sum of the five `cost_net` values.

### 7.3 Group‑Aware Budget Adjustment

Instead of ranking links by the ratio *CENT\_NET / unit\_price*, we keep the original **road‑type / RMSE proportions** and simply cap the *number* of links that can be afforded in each group.

**Algorithm per priced network**

1. Compute `cost_net`. If it is **within** the available budget → keep all links.
2. If it **exceeds** the budget:
   1. Determine the **affordable link count** for that network:\
      `quota_budget = ⌊ budget_net / unit_price[net] ⌋`
   2. Recompute group sample sizes `n_g_budget` by re‑running the RMSE‑weight formula, but with \`\`. (Same weights, smaller total.)
   3. For every road‑type group: keep the **top ****\`\`**** links** (ranked by `CENT_NET`).\
      *If **``** rounds to zero and at least one link exists, keep one link to maintain coverage.*
3. Repeat step 2 for every network that breached its cap.
4. Concatenate the adjusted per‑network samples. If a **global** `max_budget` was set (and per‑network budgets were *not*), apply the same steps but using the global quota and all five networks together.

### 7.4 Outputs

- `sample_full.shp` – quota sample before budget capping.
- `sample_budget.shp` – sample after applying budget caps (generated only if any cap was active and trimming occurred).

---

## 8. File Output Structure. File Output Structure. כתיבה לקבצים. File Output Structure. File Output Structure

```
output/
  NATIONAL/
    centrality.shp
    results.csv
  TA/
    centrality.shp
    results.csv
  JLM/ …
  HFA/ …
  BSH/ …
  summary.csv
  qc_report.csv
  cost_report.csv
```

---

## 9. Quality Control (QC)

- **External** – ensure no sampled link (Full & Budget) intersects BUF or NCB.
- **Internal** – ensure no intersections or proximities ≤ `d` between sampled links.
- Deliverables: `qc_report.csv` + pass/fail status in the GUI.

---

## 10. Key Parameters

| Parameter    | Default     | Description                                          |
| ------------ | ----------- | ---------------------------------------------------- |
| `distance_d` | 0 m         | Proximity threshold (0 = geometry intersection only) |
| `ramp_data1` | [13,14,15]  | DATA1 codes marked as “ramps”                        |
| `unit_price` | per network | Cost per count link                                  |
| `max_budget` | null        | null → no budget enforcement                         |
| `tie_break`  | ALPHABETIC  | Rule for ties in Step D                              |

---

## 11. Flow‑Chart

See the attached PNG flow‑chart for a visual summary of the entire pipeline.
