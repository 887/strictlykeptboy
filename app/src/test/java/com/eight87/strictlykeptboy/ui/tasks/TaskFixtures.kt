package com.eight87.strictlykeptboy.ui.tasks

import java.time.LocalDate

object TaskFixtures {
    val today: LocalDate = LocalDate.of(2026, 5, 12)

    val listA = TodolistInfo(id = "a", repoId = "r", name = "Alpha", emoji = "α", colorSeed = "a", priority = 3)
    val listB = TodolistInfo(id = "b", repoId = "r", name = "Bravo", emoji = "β", colorSeed = "b", priority = 1)
    val listC = TodolistInfo(id = "c", repoId = "r", name = "Charlie", emoji = "γ", colorSeed = "c", priority = 2)
    val shop = TodolistInfo(id = "s", repoId = "r", name = "Shop", emoji = "🛒", colorSeed = "s",
        mode = TodolistMode.Shopping, priority = 5)
}
