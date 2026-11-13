# Validation 4.20.0 (Forge 1.20.1)

Date: 2026-10-23

## Change: unlimited stacks instead of the wheel pocket

Pasha asked that stage four of the overfull inventory adaptation (`environment:inventory_pressure`) stop adding nine pocket slots and instead lift the 64-item stack limit.

- **Who and where.** Only a wearer with the adaptation complete (wheel worn, adaptation enabled, stage 4 — `MetaServer.full(p, POCKET)`), and only in their own inventory: hotbar, main inventory and offhand. Armour slots, chests, furnaces, crafting grids and every other container keep vanilla limits. Items that never stack (tools, armour, potions, damaged items) stay single. 16-stack items (ender pearls, snowballs, signs…) are lifted too.
- **"Infinite"** is 1 000 000 000 per slot (`core/StackCount.LIMIT`): two full stacks still add up below `Integer.MAX_VALUE`, so merging code that sums counts before clamping cannot overflow into a negative (empty) stack.
- **Stages 1–3 unchanged:** duplicate highlighting from stage 1, compaction of compatible main-inventory stacks from stage 2 (now up to the lifted limit at stage 4). The J details button is always "Compact compatible stacks".
- **Taking the wheel off** restores vanilla limits for new items; existing big stacks are never cut. Moving them into a 64-slot container splits them (shift-click puts 64 per chest slot), nothing is lost.
- **Old pocket.** 4.13–4.19 stored the pocket in the wheel item's NBT (`AdaptationPocket`). Once per second the server checks the player's inventory (worn, held, anywhere in the 41 slots) for such a wheel, removes the tag and returns its items; what does not fit is dropped at the player's feet, with a chat message either way (`InventoryPressure.returnPocket`). A wheel left in a chest is returned the first time it is back in a player's inventory.

### How the limit is lifted
`coremods/stack_limits.js` (Forge JS coremod like the existing ones, official and SRG names, each patch only rewrites single calls and leaves the method vanilla if nothing matches), helpers in `StackLimits`:
- `Inventory` (`add`, `addResource`, `hasRemainingSpaceForItem`, `placeItemBackInInventory`): `ItemStack.getMaxStackSize()` and the inventory's own 64 → `StackLimits.item` / `container`.
- `Slot.getMaxStackSize(ItemStack)`: returns the limit for the wearer's hotbar/main/offhand slots (`StackLimits.lifted`), so clicks, drags onto existing stacks and swaps follow it.
- `AbstractContainerMenu.moveItemStackTo` (shift-click): asks the slot about this item instead of comparing container and item limits separately. For vanilla slots the result is identical (`min(container, item)`); with a big stack it also stops a chest slot from receiving 64 ender pearls.
- `ItemStack.save` / `ItemStack(CompoundTag)`: `Count` is written as vanilla's byte up to 127 and as an int above; read with `getInt` (reads both). Saves made by 4.20 with stacks ≤ 127 are byte-identical to vanilla.
- `FriendlyByteBuf.writeItemStack` / `readItem`: count as VarInt instead of a byte. A VarInt below 128 is the same single byte, so ordinary stacks keep vanilla's wire format (checked by a GameTest).
- `ServerGamePacketListenerImpl.handleSetCreativeModeSlot`: the hard-coded 64 follows the same rule (`StackLimits.creativeLimit`).
- Client: `GuiGraphics.renderItemDecorations` shows counts ≥ 1000 shortened (`1.2k`, `12k`, `1.5M`, `1B`, rounded down, `core/StackCount.format`) and draws labels wider than two digits at ¾ size anchored bottom-right (`client/StackLabels`), so neighbouring labels do not run together. The tooltip of a stack ≥ 1000 shows the exact count ("Количество: 12 409"). The client mirrors the server's verdict for the local player (`StackLimits.client`) so click prediction agrees.

Removed: `server/WheelPocket` (pocket menu), network action `pocket`, lang keys `pocket.title` / `pocket.open`.

## Checks run
- Build: `release-4.20.0.ps1` — Pasha Boost 1.5.0 (unchanged) clean build and tests; wheel offline and online builds with all core tests, including the new `StackCountTest` (labels, rounding down, ≤ 4 characters up to `Integer.MAX_VALUE`, tooltip grouping, overflow headroom).
- GameTest: **61/61** (57 + 4 new in `StackLimitGameTests`):
  - `fullAdaptationLiftsTheStackLimit`: 5000 cobblestone in one slot, picked-up stacks join it, 100 ender pearls in one slot, swords stay single, helmet slot stays 1, offhand lifted; without the wheel the big stack is kept but does not grow; a player without the adaptation and one at stage 3 keep 64.
  - `shiftClickAndCompactionMergeBeyond64`: hotbar → main shift-click merges to 128; chest → inventory joins the big stack; a 500 stack shift-clicked into a chest becomes 64-item chest stacks with nothing lost; stage-4 compaction merges three 64-stacks into 192.
  - `bigStacksSurviveSavesAndPackets`: 123 456 survives `ItemStack.save`/`of` (IntTag) and `ContainerHelper`; a 64 stack still saves as a ByteTag; packet round trip of 123 456; a 64 stack is still `[present, id, 64]` on the wire; creative limit follows the adaptation.
  - `oldPocketItemsComeBack`: worn wheel's pocket returned exactly once; with a full inventory a wheel in the offhand returns its items onto the ground, none lost.
- Real client, production (SRG) names, probe `StacksQa` (groups `stacks`, `stacks-optifine`, `stacks-24mods`, `stacks-23mods-optifine`): on the server 12 409 cobblestone in one slot, 300 ender pearls, 1500 torches in the offhand, helmet slot not lifted, shift-click 128, NBT and packet round trip; the server's own player tick returns a pocket of 13 diamonds left on a second wheel; the client receives the uncut counts and knows its stacks are lifted; real clicks through `MultiPlayerGameMode` (shift-click the 12 409 stack, pick it up on the cursor, place it in the hotbar) agree on server and client with no item lost; the tooltip shows "12 409". Screenshots `stacks-inventory*.png` reviewed: `12k`, `1.5k`, `300`, `128` drawn small in their slots, chat shows the pocket message.
  - Development JAR: all four groups PASS. Two probe-fixture problems found and fixed on the way (not mod bugs): mods hand out guide books at login, so the probe now finds the stack wherever it lands; in the Eternal Tales normal world the player spawned underground, bled and died mid-test, so the probe now stands on a platform at y = 170 and is invulnerable.
  - Release JAR (`-4200` runs, all PASS): `stacks-optifine`, `stacks`, `stacks-23mods-optifine`, `stacks-24mods`; regression `screens-optifine` (13 screenshots), `slash-optifine` (3 zombies at 12/30/55 blocks through a wall), `ingame-optifine` (world visible after full landscape memory, own footsteps ignored), `forearm-optifine` (blade in first and third person, idle, swing, parry).

## Not verified
- Pasha's own world and mod set beyond the 24 tested mods. A mod that reads or writes item counts itself as a byte (its own packets or NBT) would cut stacks above 127 in its own GUI or storage; none of the 24 tested mods showed this.
- Hoppers or other automation pulling a big stack that was dropped as one item entity put it whole into an empty slot (vanilla behaviour for any stack); it is kept, not lost, but such a slot can then hold more than 64.
- Removing the mod after making big stacks: vanilla reads `Count` as a byte, so stacks above 127 saved by 4.20 would be cut or lost. Use up or split big stacks before uninstalling.
- GUI scales other than 2 for the small labels.
