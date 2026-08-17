package engine.model

enum class Resource(val techRequirement: Technology?) {
    FISH(Technology.FISHING),
    FRUIT(Technology.ORGANIZATION),
    ANIMAL(Technology.HUNTING),
    WHALES(Technology.WHALING),
    ORE(Technology.MINING),
    CROPS(Technology.FARMING),
    RUINS(null),
}
