# Issue index

Status reflects available evidence, rather than assuming every historical report remains broken. Report new problems with version, machine, settings, reproduction steps and logs. The [progression guide](../playtest-progression.md#troubleshooting) is the current test procedure.

## Open

| Report | Status / next action |
| --- | --- |
| [Reflow dwell/cooling clarity](T6_REFLOW_DWELL_COOLING_CLARITY.md) | Not independently reproduced. Record only: obtain heater, conveyor and airflow settings, measured dwell/cooling, configured bands and a screenshot before proposing a change. |

## Implemented, awaiting explicit owner confirmation

These repairs passed automated checks. Recheck the reported behavior before moving a card to completed.

| Report | Repair |
| --- | --- |
| [T6 CONTAMINATION RECALIBRATION TARGETS](T6_CONTAMINATION_RECALIBRATION_TARGETS.md) | Contamination invalidation now renews beam and quadratic targets once, persists them across reload and preserves already admitted lot contracts. |
| [T6 CREATIVE FOUP ETCH ADMISSION](T6_CREATIVE_FOUP_ETCH_ADMISSION.md) | Creative-only transport now preserves full private lot data and refreshes inventories on mode changes. Previously stripped processed lots cannot be reconstructed; use a fresh lot. |
| [T6 FOUP PREPARATION TOOLTIP](T6_FOUP_PREPARATION_TOOLTIP.md) | Public FOUP summaries and tooltips preserve substrate and preparation across processing and Creative transport. |
| [T6 MASK WRITER CORE SELECTOR OVERLAP](T6_MASK_WRITER_CORE_SELECTOR_OVERLAP.md) | Core selection and the energy display have separate layout space. |
| [T6 SCANNER ADMISSION DIAGNOSTICS](T6_SCANNER_ADMISSION_DIAGNOSTICS.md) | Scanner admission reports specific prerequisites, including missing UPW, instead of reducing all failures to a tier refusal. |
| [T6 SCANNER LAYER PROGRESS](T6_SCANNER_LAYER_PROGRESS.md) | The scanner exposes current pass/layer progress as well as the layer number. |
| [T6 UTILITY PLANT BATCH PROGRESS](T6_UTILITY_PLANT_BATCH_PROGRESS.md) | The utility plant exposes paid progress for each eight-bucket UPW batch. |

## Completed

- [CLEANROOM RESOURCE PORT CONDUIT BRIDGES](completed/CLEANROOM_RESOURCE_PORT_CONDUIT_BRIDGES.md)
- [EUV COMPONENT PLACEMENT CRASH](completed/EUV_COMPONENT_PLACEMENT_CRASH.md)
- [T7 LIGHT SOURCE CONNECTION STATUS](completed/T7_LIGHT_SOURCE_CONNECTION_STATUS.md)
- [T7 LIGHT SOURCE PANEL PLACEMENT FACING](completed/T7_LIGHT_SOURCE_PANEL_PLACEMENT_FACING.md)
- [T7 SCANNER CALIBRATION MISSING CONTROLS](completed/T7_SCANNER_CALIBRATION_MISSING_CONTROLS.md)

## Deferred features

T8 progression, NAND assembly, Encoded Logistics hardware/server integration, Ponder construction scenes, arbitrary cleanroom shapes/airlocks, equipment-bay remodeling and recycler recovery of other materials remain separate work. Reflow mechanics are unchanged in this increment.
