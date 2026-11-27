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

        val peopleById = data.people.associateBy { it.id }
        val parentLinks = data.links.filter { it.type == LinkType.PARENT }
        val levels = data.people.associate { it.id to 0 }.toMutableMap()

        // Longest known parent chain determines a person's generation.
        repeat(data.people.size) {
            var changed = false
            parentLinks.forEach { link ->
                val parentLevel = levels[link.fromId] ?: 0
                val target = parentLevel + 1
                if ((levels[link.toId] ?: 0) < target) {
                    levels[link.toId] = target
                    changed = true
                }
            }
            if (!changed) return@repeat
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
