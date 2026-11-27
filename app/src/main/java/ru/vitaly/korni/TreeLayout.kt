package ru.vitaly.korni

data class TreeNode(
    val person: Person,
    val x: Float,
    val y: Float
)

object TreeLayout {
    const val CARD_WIDTH = 188f
    const val CARD_HEIGHT = 96f
    private const val STEP_X = 226f
    private const val STEP_Y = 166f
    private const val MARGIN = 36f

    fun build(data: FamilyData): List<TreeNode> {
        if (data.people.isEmpty()) return emptyList()

        val parentLinks = data.links.filter { it.type == LinkType.PARENT }
        val sameGenerationLinks = data.links.filter {
            it.type == LinkType.SPOUSE || it.type == LinkType.SIBLING
        }
        val levels = data.people.associate { it.id to 0 }.toMutableMap()

        // Супруги и братья/сёстры располагаются на одном уровне.
        // Родительские связи задают переход к следующему поколению.
        for (iteration in data.people.indices) {
            var changed = false

            sameGenerationLinks.forEach { link ->
                val commonLevel = maxOf(levels[link.fromId] ?: 0, levels[link.toId] ?: 0)
                if ((levels[link.fromId] ?: 0) != commonLevel) {
                    levels[link.fromId] = commonLevel
                    changed = true
                }
                if ((levels[link.toId] ?: 0) != commonLevel) {
                    levels[link.toId] = commonLevel
                    changed = true
                }
            }

            parentLinks.forEach { link ->
                val target = (levels[link.fromId] ?: 0) + 1
                if ((levels[link.toId] ?: 0) < target) {
                    levels[link.toId] = target
                    changed = true
                }
            }

            if (!changed) break
        }

        val byLevel = data.people.groupBy { levels[it.id] ?: 0 }.toSortedMap()
        val positions = mutableMapOf<String, TreeNode>()
        byLevel.forEach { (level, members) ->
            val sorted = members.sortedWith(
                compareBy<Person> {
                    val parentIds = parentLinks.filter { link -> link.toId == it.id }.map { link -> link.fromId }
                    val knownParentX = parentIds.mapNotNull { id -> positions[id]?.x }
                    if (knownParentX.isEmpty()) Float.MAX_VALUE else knownParentX.average().toFloat()
                }.thenBy { it.lastName.lowercase() }
                    .thenBy { it.firstName.lowercase() }
            )
            sorted.forEachIndexed { index, person ->
                positions[person.id] = TreeNode(
                    person = person,
                    x = MARGIN + index * STEP_X,
                    y = MARGIN + level * STEP_Y
                )
            }
        }
        return data.people.mapNotNull { positions[it.id] }
    }

    fun width(nodes: List<TreeNode>): Float =
        (nodes.maxOfOrNull { it.x + CARD_WIDTH } ?: 0f) + MARGIN

    fun height(nodes: List<TreeNode>): Float =
        (nodes.maxOfOrNull { it.y + CARD_HEIGHT } ?: 0f) + MARGIN
}
