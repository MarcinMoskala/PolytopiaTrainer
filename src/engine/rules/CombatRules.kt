package engine.rules

import engine.model.*
import engine.model.Unit
import kotlin.math.roundToInt

object CombatRules {
    private const val ATTACK_MODIFIER = 4.5
    private const val DEFENCE_BONUS = 1.5
    private const val DEFENCE_IN_WALLS = 4.0

    data class CombatResult(
        val attackDamage: Int,
        val retaliationDamage: Int,
    )

    fun calculate(
        attacker: Unit,
        target: Unit,
        targetTile: Tile,
        targetCity: City?,
        targetTribe: Tribe,
    ): CombatResult {
        val attackForce = attacker.attack * (attacker.currentHp.toDouble() / attacker.maxHp)
        var defenceForce = target.defense * (target.currentHp.toDouble() / target.maxHp)

        // Defence bonuses
        if (targetTile.terrain == Terrain.CITY && targetCity != null && targetTribe.controlsCity(targetCity.id)) {
            defenceForce *= if (targetCity.hasWalls) DEFENCE_IN_WALLS
                            else if (target.type.canFortify()) DEFENCE_BONUS
                            else 1.0
        } else if (
            (targetTile.terrain == Terrain.MOUNTAIN && targetTribe.hasResearched(Technology.MEDITATION)) ||
            (targetTile.terrain.isWater() && targetTribe.hasResearched(Technology.AQUATISM)) ||
            (targetTile.terrain == Terrain.FOREST && targetTribe.hasResearched(Technology.ARCHERY))
        ) {
            defenceForce *= DEFENCE_BONUS
        }

        val total = attackForce + defenceForce
        val attackDamage = ((attackForce / total) * attacker.attack * ATTACK_MODIFIER).roundToInt()
        val retaliationDamage = ((defenceForce / total) * target.defense * ATTACK_MODIFIER).roundToInt()

        return CombatResult(attackDamage, retaliationDamage)
    }
}
