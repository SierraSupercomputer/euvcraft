# Scanner resource integration

Eight new block/client-item/model/loot/crafting resources, pickaxe tags and English titles are present. Joined controller and upper operation faces reuse the existing T4 large-panel assets. The separate calibration panel uses an all-side console face; module sides use their corresponding existing equipment icons. These are provisional reused assets.

The controller/modules/panels consume diamonds, gold blocks and Advanced Fab Casings. The controller consumes T3 logic; physical modules each consume their corresponding existing T4 equipment item. Recipes have no direct T4-chip dependency. All costs remain editable JSON.

Nemotron's resource attempt timed out with two blockstate files; the coordinator preserved that draft and completed the resource set. ScannerResourceTest validates every new asset reference, texture, title, recipe result/bootstrap and module ingredient. The source-derived recipe guide now inventories73 JSON recipes. Client appearance and usability remain owner walkthrough checks.
