package com.sonas.actiontracker.data

import kotlin.random.Random

/** One short quote for the daily Thought of the day task, and who said it. */
data class Thought(val text: String, val source: String)

/**
 * Thought of the day: one line a day, added as a task by [com.sonas.actiontracker.sync.ThoughtTasks],
 * from the Stoics, the Bhagavad Gita, the Thirukkural, Rumi and Peter Drucker (English renderings;
 * translations vary).
 */
object Thoughts {
    val all: List<Thought> = listOf(
        Thought("We suffer more often in imagination than in reality.", "Seneca"),
        Thought("It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"),
        Thought("The happiness of your life depends upon the quality of your thoughts.", "Marcus Aurelius"),
        Thought("Waste no more time arguing what a good man should be. Be one.", "Marcus Aurelius"),
        Thought("What stands in the way becomes the way.", "Marcus Aurelius"),
        Thought("No man is free who is not master of himself.", "Epictetus"),
        Thought("First say to yourself what you would be; then do what you have to do.", "Epictetus"),
        Thought("Wealth consists not in having great possessions, but in having few wants.", "Epictetus"),

        Thought("You have a right to your actions, but never to their fruits.", "Bhagavad Gita 2.47"),
        Thought("Yoga is skill in action.", "Bhagavad Gita 2.50"),
        Thought("Better your own duty done imperfectly than another's done well.", "Bhagavad Gita 3.35"),
        Thought("Lift yourself by your own self; do not let yourself fall.", "Bhagavad Gita 6.5"),
        Thought("For one who has conquered the mind, the mind is the best of friends.", "Bhagavad Gita 6.6"),
        Thought("Do your work with an even mind, alike in success and failure.", "Bhagavad Gita 2.48"),

        Thought("A help given in time of need, though small, is greater than the world.", "Thirukkural 102"),
        Thought("Harsh words when kind ones are at hand: like eating unripe fruit when ripe is there.", "Thirukkural 100"),
        Thought("Learn thoroughly what is worth learning, then live by it.", "Thirukkural 391"),
        Thought("The deeper you dig, the more the spring yields; the more you learn, the more you know.", "Thirukkural 396"),
        Thought("Repay those who harm you with kindness, and let both be forgotten.", "Thirukkural 314"),
        Thought("Even when fate denies the goal, effort pays its own wages.", "Thirukkural 619"),
        Thought("Easy to say; hard to do what one has said.", "Thirukkural 664"),

        Thought("Yesterday I was clever, so I wanted to change the world. Today I am wise, so I am changing myself.", "Rumi"),
        Thought("Let the beauty of what you love be what you do.", "Rumi"),
        Thought("Raise your words, not your voice. It is rain that grows flowers, not thunder.", "Rumi"),
        Thought("The wound is the place where the light enters you.", "Rumi"),
        Thought("Silence is the language of God; all else is poor translation.", "Rumi"),

        Thought("Efficiency is doing things right; effectiveness is doing the right things.", "Peter Drucker"),
        Thought("There is nothing so useless as doing efficiently what should not be done at all.", "Peter Drucker"),
        Thought("Time is the scarcest resource; unless it is managed, nothing else can be managed.", "Peter Drucker"),
        Thought("Plans are only good intentions unless they degenerate into hard work.", "Peter Drucker"),
        Thought("The most important thing in communication is hearing what isn't said.", "Peter Drucker"),
        Thought("Follow effective action with quiet reflection; it brings even more effective action.", "Peter Drucker")
    )

    /** Index of a random thought other than [last], so two days in a row never repeat. */
    fun next(last: Int, random: Random = Random.Default): Int {
        if (last !in all.indices) return random.nextInt(all.size)
        val i = random.nextInt(all.size - 1)
        return if (i >= last) i + 1 else i
    }
}
