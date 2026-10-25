# Cleanroom item/fluid ports between Arcforge pipes

Status: repair implemented; owner recheck pending. Automated verification evidence is retained separately by the coordinator.

Both original handlers forwarded to the opposite adjacent block. Arcforge pipes are network nodes without ordinary endpoint inventories/tanks, so pipes on both sides could not exchange contents. Native regressions reproduced both failures on unchanged production code, while conservation assertions passed.

Repair: saved generic transactional item/fluid endpoints, axis-only access, live retained-handler guards, direct receiver delivery and item drops on breaking. No progression or cleanroom-rule changes. See ../manual-walkthrough-resource-ports.md.
