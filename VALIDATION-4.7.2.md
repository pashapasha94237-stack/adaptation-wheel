# Validation 4.7.2 — World Slash

Minecraft Java 1.20.1, Forge 47.4.16, Java 17, Jujutsu Craft v51 RU.

Offline Gradle build, coreTest and compileGametestJava pass.
The threshold regression test now expects exactly 100 HP to qualify, and 99 HP not to.
Runtime integration executes real player item.use callbacks and the actual Gojo hurt
pipeline. Progress is advanced in the test instead of waiting for all stage timers.

Verified in isolated client / integrated-server world:
- A mob with exactly 100 max HP qualifies for the boss/existence progression.
- No prior adaptation or stage 4 alone does not unlock World Slash.
- Stage 5 of a different boss (Wither) unlocks it, without Infinity adaptation.
- Shift + item.use switches from parry to World Slash.
- Actual item.use sends the cut six blocks ahead and lowers Gojo's real health
  while his original Infinity effect remains active.
- Ordinary attacks before and after the cut are blocked by Infinity: bypass is scoped
  to the synchronous slash attack, not permanently granted by learning another boss.
- Off-axis target health is unchanged.
- Each accepted cut spends exactly three durability; immediate reuse does not damage
  again or spend durability because the item cooldown is active.
- Disabling the learned boss entry removes the reward; returning to parry remains possible.
- Unequipping the wheel removes the reward; saved state retains the fifth-stage prerequisite.
- The existing Infinity, all 259 JJC techniques and harmful/service/positive effect
  integration assertions also pass.

The slash uses configured sword attributes and existing positive-energy mob multipliers.
It clips solid blocks at 24 blocks, uses normal damage callbacks (including a dragon head
callback for the multipart dragon) and keeps PvP/team checks. Existing configured
one-hit execution applies only to the separately adapted boss species when enabled.
The new reward requires existence adaptation enabled; one-hit-kill enabled is not required.

The generic World Slash itself has no dependency on Jujutsu Craft classes. Infinity
compatibility targets Jujutsu Craft v51. A live session with two human players and the
new dragon-specific slash branch have not been separately runtime-tested.

Final JAR SHA-256:
0261145EC0D5F2B83EFA3666D1DDA44D8D6738312CA4D86C7CDF103D17B3888D

Final runtime directory:
jujutsucraft-russian-1.20.1/probe/run-world-slash-final-472-20261021-055141
Expected markers: JJC_WORLD_SLASH_QA PASS, JJC_INFINITY_QA PASS, JJC_WHEEL_QA PASS.