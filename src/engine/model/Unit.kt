package engine.model

enum class TurnStatus {
    FRESH,
    MOVED,
    ATTACKED,
    MOVED_AND_ATTACKED,
    PUSHED,
    FINISHED,
}

data class Unit(
    val id: Int,
    val type: UnitType,
    val tribeId: Int,
    val position: Position,
    val currentHp: Int = type.maxHp,
    val kills: Int = 0,
    val isVeteran: Boolean = false,
    val turnStatus: TurnStatus = TurnStatus.FRESH,
) {
    val maxHp: Int get() = if (isVeteran) type.maxHp + VETERAN_PLUS_HP else type.maxHp
    val attack: Int get() = type.attack
    val defense: Int get() = type.defense
    val movement: Int get() = type.movement
    val range: Int get() = type.range

    fun canAttack(): Boolean = turnStatus == TurnStatus.FRESH || turnStatus == TurnStatus.MOVED

    fun canMove(): Boolean = turnStatus == TurnStatus.FRESH

    fun shouldPromoteToVeteran(): Boolean = !isVeteran && kills >= VETERAN_KILLS

    fun withHp(hp: Int): Unit = copy(currentHp = hp.coerceIn(0, maxHp))

    fun withStatus(status: TurnStatus): Unit = copy(turnStatus = status)

    companion object {
        const val VETERAN_KILLS = 3
        const val VETERAN_PLUS_HP = 5
        const val RECOVER_PLUS_HP = 2
        const val RECOVER_IN_BORDERS_PLUS_HP = 2
        const val MINDBENDER_HEAL = 4
    }
}
