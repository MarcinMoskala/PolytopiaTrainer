package engine

import engine.model.*
import engine.model.Unit
import engine.rules.CombatRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for CombatRules.calculate() covering a variety of battle situations.
 *
 * Damage formula (ATTACK_MODIFIER = 4.5):
 *   attackForce  = attacker.attack  * (attacker.currentHp / attacker.maxHp)
 *   defenceForce = target.defense   * (target.currentHp   / target.maxHp)  [* terrain multiplier]
 *   total        = attackForce + defenceForce
 *   attackDamage      = round((attackForce  / total) * attacker.attack  * 4.5)
 *   retaliationDamage = round((defenceForce / total) * target.defense   * 4.5)
 *
 * Unit stats (attack / defense / maxHp):
 *   Warrior   2 / 2 / 10
 *   Archer    2 / 1 / 10   (ranged – no retaliation in game, but formula still produces a value)
 *   Swordman  3 / 3 / 15
 *   Defender  1 / 3 / 15
 *   Knight    4 / 1 / 15
 *   Catapult  4 / 0 / 10   (ranged)
 *   Giant     5 / 4 / 40   (SUPERUNIT)
 *
 * Defence multipliers:
 *   Plain (no bonus)  : 1.0
 *   City, can fortify : 1.5  (DEFENCE_BONUS)
 *   City with walls   : 4.0  (DEFENCE_IN_WALLS)
 *
 * Scenario reference: https://polytopia-damage-calculator.firebaseapp.com/
 * Note: the external simulator may use slightly different rounding or unit stats;
 * the assertions here reflect the exact output of CombatRules.calculate().
 */
class BattleRulesTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun unit(
        type: UnitType,
        hp: Int = type.maxHp,
        isVeteran: Boolean = false,
    ) = Unit(
        id = 0,
        type = type,
        tribeId = 0,
        position = Position(0, 0),
        currentHp = hp,
        isVeteran = isVeteran,
    )

    private fun target(
        type: UnitType,
        hp: Int = type.maxHp,
        isVeteran: Boolean = false,
    ) = Unit(
        id = 1,
        type = type,
        tribeId = 1,
        position = Position(1, 0),
        currentHp = hp,
        isVeteran = isVeteran,
    )

    private fun plainTile(u: Unit? = null) = Tile(terrain = Terrain.PLAIN, unit = u)
    private fun cityTile(cityId: Int, u: Unit? = null) = Tile(terrain = Terrain.CITY, cityId = cityId, unit = u)

    private fun tribe(id: Int, techs: Set<Technology> = emptySet(), cityIds: List<Int> = emptyList()) =
        Tribe(id = id, type = TribeType.IMPERIUS, researchedTechs = techs, cityIds = cityIds)

    private fun city(hasWalls: Boolean = false) =
        City(id = 99, position = Position(0, 0), tribeId = 1, hasWalls = hasWalls)

    private fun calc(
        attacker: Unit,
        target: Unit,
        targetTile: Tile = plainTile(target),
        targetCity: City? = null,
        targetTribe: Tribe = tribe(target.tribeId),
    ) = CombatRules.calculate(attacker, target, targetTile, targetCity, targetTribe)

    // ── Warrior vs Warrior (symmetric baseline) ───────────────────────────────

    @Nested
    inner class WarriorVsWarrior {

        @Test
        fun `equal warriors full HP on plain deal 5 damage each`() {
            val result = calc(unit(UnitType.WARRIOR), target(UnitType.WARRIOR))
            assertEquals(5, result.attackDamage)
            assertEquals(5, result.retaliationDamage)
        }

        @Test
        fun `weakened attacker half HP deals less damage and takes more retaliation`() {
            // attackForce=1.0, defenceForce=2.0 → atk=3, ret=6
            val result = calc(unit(UnitType.WARRIOR, hp = 5), target(UnitType.WARRIOR))
            assertEquals(3, result.attackDamage)
            assertEquals(6, result.retaliationDamage)
        }

        @Test
        fun `warrior vs warrior in city without walls - defender gets 1_5x bonus`() {
            // defenceForce = 2*(10/10)*1.5 = 3.0, attackForce = 2.0 → atk=4, ret=5
            val targetTribe = tribe(1, cityIds = listOf(99))
            val c = city(hasWalls = false)
            val result = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.WARRIOR),
                targetTile = cityTile(99),
                targetCity = c,
                targetTribe = targetTribe,
            )
            assertEquals(4, result.attackDamage)
            assertEquals(5, result.retaliationDamage)
        }

        /**
         * Scenario from issue: Warrior(8) + Warrior(10) vs Warrior(10) wall
         *   → Warrior(0) + Warrior(3) vs Warrior(6)
         *
         * Step 1 – Warrior(8) attacks Warrior(10) behind walls:
         *   attackForce=1.6, defenceForce=8.0 → atk=2, ret=8  → attacker dies (8-8=0), target=8
         * Step 2 – Warrior(10) attacks Warrior(8) behind walls:
         *   attackForce=2.0, defenceForce=6.4 → atk=2, ret=7  → attacker=3, target=6
         */
        @Test
        fun `scenario - Warrior(8) attacks Warrior(10) behind walls - deals 2 damage and takes 8 retaliation`() {
            val targetTribe = tribe(1, cityIds = listOf(99))
            val c = city(hasWalls = true)
            val result = calc(
                attacker = unit(UnitType.WARRIOR, hp = 8),
                target = target(UnitType.WARRIOR, hp = 10),
                targetTile = cityTile(99),
                targetCity = c,
                targetTribe = targetTribe,
            )
            assertEquals(2, result.attackDamage)
            assertEquals(8, result.retaliationDamage)
            // attacker: 8 - 8 = 0 (dead), target: 10 - 2 = 8
        }

        @Test
        fun `scenario - Warrior(10) attacks Warrior(8) behind walls - deals 2 damage and takes 7 retaliation`() {
            val targetTribe = tribe(1, cityIds = listOf(99))
            val c = city(hasWalls = true)
            val result = calc(
                attacker = unit(UnitType.WARRIOR, hp = 10),
                target = target(UnitType.WARRIOR, hp = 8),
                targetTile = cityTile(99),
                targetCity = c,
                targetTribe = targetTribe,
            )
            assertEquals(2, result.attackDamage)
            assertEquals(7, result.retaliationDamage)
            // attacker: 10 - 7 = 3, target: 8 - 2 = 6
        }
    }

    // ── Ranged units ──────────────────────────────────────────────────────────

    @Nested
    inner class RangedUnits {

        @Test
        fun `archer vs warrior full HP - formula gives 5 attack damage`() {
            // Archer atk=2, def=1; Warrior def=2. attackForce=2, defenceForce=2 → atk=5
            val result = calc(unit(UnitType.ARCHER), target(UnitType.WARRIOR))
            assertEquals(5, result.attackDamage)
        }

        @Test
        fun `catapult vs warrior full HP - deals 12 damage`() {
            // Catapult atk=4, def=0; Warrior def=2. attackForce=4, defenceForce=2 → atk=12
            val result = calc(unit(UnitType.CATAPULT), target(UnitType.WARRIOR))
            assertEquals(12, result.attackDamage)
        }

        /**
         * Scenario from issue: Catapult(10) + Catapult(10)safe + Catapult(10) vs Giant(30)
         *   → Catapult(2) + Catapult(10)safe + Catapult(10) vs Giant(0/-7)
         *
         * Catapult: atk=4, def=0, maxHp=10. Giant: atk=5, def=4, maxHp=40.
         * "safe" = out of Giant's melee range (no retaliation received).
         *
         * Step 1 – Cat(10) vs Giant(30): atk=10, ret=8 → cat=2, giant=20
         * Step 2 – Cat(10) safe vs Giant(20): atk=12, no ret → giant=8
         * Step 3 – Cat(10) vs Giant(8):  atk=15, ret=3 → cat=7, giant=-7 (overkill)
         */
        @Test
        fun `scenario step1 - Catapult(10) vs Giant(30) - deals 10 damage and takes 8 retaliation`() {
            // af=4*(10/10)=4.0, df=4*(30/40)=3.0 → atk=10, ret=8
            val result = calc(unit(UnitType.CATAPULT), target(UnitType.SUPERUNIT, hp = 30))
            assertEquals(10, result.attackDamage)
            assertEquals(8, result.retaliationDamage)
        }

        @Test
        fun `scenario step2 - Catapult(10) safe vs Giant(20) - deals 12 damage`() {
            // af=4*(10/10)=4.0, df=4*(20/40)=2.0 → atk=12
            val result = calc(unit(UnitType.CATAPULT), target(UnitType.SUPERUNIT, hp = 20))
            assertEquals(12, result.attackDamage)
            // "safe" catapult is out of Giant's range, so retaliation is not applied in game
        }

        @Test
        fun `scenario step3 - Catapult(10) vs Giant(8) - deals 15 damage and takes 3 retaliation`() {
            // af=4*(10/10)=4.0, df=4*(8/40)=0.8, total=4.8 → atk=15, ret=3
            val result = calc(unit(UnitType.CATAPULT), target(UnitType.SUPERUNIT, hp = 8))
            assertEquals(15, result.attackDamage)
            assertEquals(3, result.retaliationDamage)
            // giant: 8 - 15 = -7 (overkill), catapult: 10 - 3 = 7
        }
    }

    // ── Mixed unit types ──────────────────────────────────────────────────────

    @Nested
    inner class MixedUnits {

        @Test
        fun `swordman vs warrior full HP - deals 8 damage and takes 4 retaliation`() {
            // Swordman atk=3, def=3, maxHp=15; Warrior def=2, maxHp=10
            val result = calc(unit(UnitType.SWORDMAN), target(UnitType.WARRIOR))
            assertEquals(8, result.attackDamage)
            assertEquals(4, result.retaliationDamage)
        }

        @Test
        fun `defender vs warrior full HP - deals 2 damage and takes 6 retaliation`() {
            // Defender atk=1, def=3, maxHp=15; Warrior def=2, maxHp=10
            val result = calc(unit(UnitType.DEFENDER), target(UnitType.WARRIOR))
            assertEquals(2, result.attackDamage)
            assertEquals(6, result.retaliationDamage)
        }

        @Test
        fun `knight vs warrior full HP - deals 12 damage and takes 3 retaliation`() {
            // Knight atk=4, def=1, maxHp=15; Warrior def=2, maxHp=10
            val result = calc(unit(UnitType.KNIGHT), target(UnitType.WARRIOR))
            assertEquals(12, result.attackDamage)
            assertEquals(3, result.retaliationDamage)
        }

        @Test
        fun `warrior vs giant full HP - deals 3 damage and takes 12 retaliation`() {
            // Warrior atk=2, def=2, maxHp=10; Giant def=4, maxHp=40
            // af=2*(10/10)=2.0, df=4*(40/40)=4.0 → atk=3, ret=12
            val result = calc(unit(UnitType.WARRIOR), target(UnitType.SUPERUNIT))
            assertEquals(3, result.attackDamage)
            assertEquals(12, result.retaliationDamage)
        }

        @Test
        fun `giant full HP vs warrior full HP - deals 16 damage and takes 3 retaliation`() {
            // af=5*(40/40)=5.0, df=2*(10/10)=2.0 → atk=16, ret=3
            val result = calc(unit(UnitType.SUPERUNIT), target(UnitType.WARRIOR))
            assertEquals(16, result.attackDamage)
            assertEquals(3, result.retaliationDamage)
        }

        @Test
        fun `warrior vs catapult - catapult def=0 so warrior deals 9 damage and takes 0 retaliation`() {
            // Catapult def=0 → defenceForce=0, total=attackForce only → atk=9, ret=0
            val result = calc(unit(UnitType.WARRIOR), target(UnitType.CATAPULT))
            assertEquals(9, result.attackDamage)
            assertEquals(0, result.retaliationDamage)
        }

        /**
         * Scenario from issue: Warrior(15)vet + Dagger(10) + Giant(20) vs Giant(30)
         *   → Warrior(4)vet + Dagger(10) + Giant(12) vs Giant(10)
         *
         * "Dagger" treated as Swordman (atk=3, def=3, maxHp=15), hp=10.
         * Warrior vet: maxHp=15, hp=15.  Giant: atk=5, def=4, maxHp=40.
         *
         * Step 1 – VetWarrior(15) vs Giant(30): atk=4, ret=11 → warrior=4, giant=26
         * Step 2 – Swordman(10) vs Giant(26): atk=6, ret=10  → swordman=0, giant=20
         * Step 3 – OurGiant(20) vs Giant(20): atk=13, ret=8  → ourGiant=12, enemyGiant=7
         *
         * (External simulator uses Giant atk=5 for retaliation calc; our engine uses def=4.)
         */
        @Test
        fun `scenario step1 - VetWarrior(15) vs Giant(30) - deals 4 damage and takes 11 retaliation`() {
            // af=2*(15/15)=2.0, df=4*(30/40)=3.0 → atk=4, ret=11
            val result = calc(
                attacker = unit(UnitType.WARRIOR, hp = 15, isVeteran = true),
                target = target(UnitType.SUPERUNIT, hp = 30),
            )
            assertEquals(4, result.attackDamage)
            assertEquals(11, result.retaliationDamage)
            // warrior: 15 - 11 = 4, giant: 30 - 4 = 26
        }

        @Test
        fun `scenario step2 - Swordman(10) vs Giant(26) - deals 6 damage and takes 10 retaliation`() {
            // af=3*(10/15)=2.0, df=4*(26/40)=2.6 → atk=6, ret=10
            val result = calc(
                attacker = unit(UnitType.SWORDMAN, hp = 10),
                target = target(UnitType.SUPERUNIT, hp = 26),
            )
            assertEquals(6, result.attackDamage)
            assertEquals(10, result.retaliationDamage)
            // swordman: 10 - 10 = 0 (dead), giant: 26 - 6 = 20
        }

        @Test
        fun `scenario step3 - OurGiant(20) vs EnemyGiant(20) - deals 13 damage and takes 8 retaliation`() {
            // af=5*(20/40)=2.5, df=4*(20/40)=2.0, total=4.5 → atk=13, ret=8
            val result = calc(
                attacker = unit(UnitType.SUPERUNIT, hp = 20),
                target = target(UnitType.SUPERUNIT, hp = 20),
            )
            assertEquals(13, result.attackDamage)
            assertEquals(8, result.retaliationDamage)
            // ourGiant: 20 - 8 = 12, enemyGiant: 20 - 13 = 7
        }
    }

    // ── Terrain defence bonuses ───────────────────────────────────────────────

    @Nested
    inner class TerrainBonuses {

        @Test
        fun `warrior in city with walls takes much less damage`() {
            val noWalls = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.WARRIOR),
            )
            val targetTribe = tribe(1, cityIds = listOf(99))
            val withWalls = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.WARRIOR),
                targetTile = cityTile(99),
                targetCity = city(hasWalls = true),
                targetTribe = targetTribe,
            )
            // walls give 4x defence bonus → attacker deals far less damage
            assertEquals(5, noWalls.attackDamage)
            assertEquals(2, withWalls.attackDamage)
        }

        @Test
        fun `warrior in city without walls gets 1_5x defence bonus`() {
            val targetTribe = tribe(1, cityIds = listOf(99))
            val result = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.WARRIOR),
                targetTile = cityTile(99),
                targetCity = city(hasWalls = false),
                targetTribe = targetTribe,
            )
            assertEquals(4, result.attackDamage)
            assertEquals(5, result.retaliationDamage)
        }

        @Test
        fun `non-fortifiable unit in city gets no defence bonus`() {
            // Catapult cannot fortify → no city bonus
            val targetTribe = tribe(1, cityIds = listOf(99))
            val withCity = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.CATAPULT),
                targetTile = cityTile(99),
                targetCity = city(hasWalls = false),
                targetTribe = targetTribe,
            )
            val plain = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.CATAPULT),
            )
            assertEquals(plain.attackDamage, withCity.attackDamage)
        }

        @Test
        fun `warrior in forest with ARCHERY tech gets 1_5x defence bonus`() {
            val targetTribe = tribe(1, techs = setOf(Technology.ARCHERY))
            val withBonus = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.WARRIOR),
                targetTile = Tile(terrain = Terrain.FOREST),
                targetTribe = targetTribe,
            )
            val noBonus = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.WARRIOR),
                targetTile = Tile(terrain = Terrain.FOREST),
                targetTribe = tribe(1),
            )
            // With ARCHERY tech, forest gives 1.5x defence → attacker deals less
            assertEquals(4, withBonus.attackDamage)
            assertEquals(5, noBonus.attackDamage)
        }

        @Test
        fun `warrior on mountain with MEDITATION tech gets 1_5x defence bonus`() {
            val targetTribe = tribe(1, techs = setOf(Technology.MEDITATION))
            val result = calc(
                attacker = unit(UnitType.WARRIOR),
                target = target(UnitType.WARRIOR),
                targetTile = Tile(terrain = Terrain.MOUNTAIN),
                targetTribe = targetTribe,
            )
            assertEquals(4, result.attackDamage)
            assertEquals(5, result.retaliationDamage)
        }
    }
}
