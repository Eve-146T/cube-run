package cube.run.game.track

/**
 * What a section is mostly about, for grouping it in the section explorer.
 * Read off its shape ([Sections.preview]), so a new section files itself:
 * coin feasts are COINS, four or more different obstacles make it MIXED, plain
 * dodge/jump/duck/slider rows are MOVES, and otherwise the most telling special
 * obstacle decides, in the order PITS, ROOFS, CRUSHERS, MACHINES.
 */
enum class SectionFamily {
    MOVES, ROOFS, MACHINES, CRUSHERS, PITS, MIXED, COINS;

    companion object {
        /** One obstacle per preview cell kind (pads and tall walls count as one); 0 is no obstacle. */
        private fun obstacle(cell: Int): Int = when (cell) {
            Sections.C_PAD, Sections.C_TALL -> Sections.C_PAD
            Sections.C_COIN -> 0
            else -> cell
        }

        private fun of(obstacle: Int): SectionFamily = when (obstacle) {
            Sections.C_TAR -> PITS
            Sections.C_PLAT, Sections.C_PAD -> ROOFS
            Sections.C_PINCER, Sections.C_STOMP -> CRUSHERS
            Sections.C_PISTON, Sections.C_SWEEP, Sections.C_PENDULUM -> MACHINES
            else -> MOVES
        }

        fun of(s: Sect): SectionFamily = of(Sections.preview(s))

        internal fun of(cells: List<IntArray>): SectionFamily {
            if (cells.count { row -> Sections.C_COIN in row } >= 2) return COINS
            val obstacles = cells.flatMap { row -> row.map(::obstacle) }.filter { it != 0 }.toSet()
            if (obstacles.size >= 4) return MIXED
            val families = obstacles.map(::of).toSet()
            return listOf(PITS, ROOFS, CRUSHERS, MACHINES).firstOrNull { it in families } ?: MOVES
        }
    }
}
