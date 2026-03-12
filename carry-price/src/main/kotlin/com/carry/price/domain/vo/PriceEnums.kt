package com.carry.price.domain.vo

enum class OrderUnitType {
    SOLO,
}

enum class OrderRequestType {
    NEW,
    REORDER,
}

enum class LaundryItemType {
    REGULAR,
    BLANKET,
    SHOES,
    REGULAR_AND_BLANKET,
}

enum class OptionType {
    WASH,
    DRY,
    ADDITIONAL,
}

enum class SubOptionType {
    // Wash
    STANDARD,
    HOT_WATER,

    // Dry
    LOW_HEAT,
    HIGH_HEAT,

    // Additional
    FOLD_LAUNDRY,
    ADD_SOFTENER,
}
