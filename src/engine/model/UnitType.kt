package engine.model

enum class UnitType(
    val cost: Int,
    val attack: Int,
    val defense: Int,
    val movement: Int,
    val maxHp: Int,
    val range: Int,
    val techRequirement: Technology?,
    val points: Int,
) {
    WARRIOR(2, 2, 2, 1, 10, 1, null, 10),
    RIDER(3, 2, 1, 2, 10, 1, Technology.RIDING, 15),
    DEFENDER(3, 1, 3, 1, 15, 1, Technology.SHIELDS, 15),
    SWORDMAN(5, 3, 3, 1, 15, 1, Technology.SMITHERY, 25),
    ARCHER(3, 2, 1, 1, 10, 2, Technology.ARCHERY, 15),
    CATAPULT(8, 4, 0, 1, 10, 3, Technology.MATHEMATICS, 40),
    KNIGHT(8, 4, 1, 3, 15, 1, Technology.CHIVALRY, 40),
    MIND_BENDER(5, 0, 1, 1, 10, 1, Technology.PHILOSOPHY, 25),
    BOAT(0, 1, 1, 2, 10, 2, Technology.SAILING, 0),
    SHIP(5, 2, 2, 3, 10, 2, Technology.SAILING, 0),
    BATTLESHIP(15, 4, 3, 3, 10, 2, Technology.NAVIGATION, 0),
    SUPERUNIT(10, 5, 4, 1, 40, 1, null, 50);

    fun isWaterUnit(): Boolean = this == BOAT || this == SHIP || this == BATTLESHIP

    fun isRanged(): Boolean = this in setOf(BOAT, SHIP, BATTLESHIP, ARCHER, CATAPULT)

    fun canFortify(): Boolean = this in setOf(WARRIOR, RIDER, ARCHER, DEFENDER, SWORDMAN, KNIGHT)

    fun isSpawnable(): Boolean = !isWaterUnit() && this != SUPERUNIT
}
