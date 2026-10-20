# T3 support multiblock foundation

Two separate 2x2x3 support cells are registered: Boule Purifier and E-beam Mask Writer. Each has a lower-front controller in either column, one or two Support Power Ports and Support Casings elsewhere. One controller owns inventory, FE and jobs. Only formed, owned power ports expose FE. Members retain no independent inventory, fluid or energy storage. Both menus retain fixed slot indices and player inventory coordinates.

Actual production/test compilation passed at ede8235. All nine focused foundation server cases passed at 2f539351a2d8a77ce1def41a770a31e8cee8b729: both machine kinds, four facings and both anchor columns; ordered part indices; removal/reformation; visual repair; stale/replaced controller ownership; adjacent/overlapping cells; capabilities; empty persistent water tank and definition bounds; and menu indices/coordinates/validity. Initial failing fixture setups and all repairs remain in Git/history and check receipts.

The retained no-generic-runner case is subsequently adapted to require no native job, FE consumption or water creation when water is absent; the purifier now intentionally has its own job engine. These later changes await the combined server suite. New support blocks reuse the approved joined Process Cell models through generated aliases. Client rendering remains owner-pending.

An advisory Nemotron review approved the earlier native code but incorrectly described its test count as nine passes; that model statement is not evidence. The exact coordinator server XML at 2f539 is the authority for the measured nine passes. Final whole-T3 reviews remain pending.
