package com.evyr.rebuild.tarot

/** Which of the four Minor Arcana suits, or Major for the 22 trump cards. */
enum class Arcana { MAJOR, WANDS, CUPS, SWORDS, PENTACLES }

/**
 * A single tarot card: its name, arcana/suit, a short ASCII glyph drawn in the
 * pull result (deliberately small and blocky -- this renders in a monospace
 * terminal font at a small size, not as real art), and upright/reversed
 * meanings. Meanings are short and interpretive by design -- tarot readings
 * are a reflection prompt, not a prediction, and the app doesn't pretend
 * otherwise (see the "what this is" explainer text shown alongside pulls).
 */
data class TarotCard(
    val name: String,
    val arcana: Arcana,
    val glyph: String,
    val uprightMeaning: String,
    val reversedMeaning: String
)

object TarotDeck {

    /** Y = a wand. Pip count for Ace-10, distinct figure art for the four court cards. */
    private fun wandGlyph(rank: Int): String = when (rank) {
        1 -> """
            .-----------.
            |     Y     |
            |           |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        2 -> """
            .-----------.
            |   Y   Y   |
            |           |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        3 -> """
            .-----------.
            |  Y  Y  Y  |
            |           |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        4 -> """
            .-----------.
            |  Y Y Y Y  |
            |           |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        5 -> """
            .-----------.
            |  Y Y Y Y  |
            |     Y     |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        6 -> """
            .-----------.
            |  Y Y Y Y  |
            |    Y Y    |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        7 -> """
            .-----------.
            |  Y Y Y Y  |
            |   Y Y Y   |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        8 -> """
            .-----------.
            |  Y Y Y Y  |
            |  Y Y Y Y  |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        9 -> """
            .-----------.
            |  Y Y Y Y  |
            |  Y Y Y Y  |
            |     Y     |
            |           |
            '-----------'
        """.trimIndent()
        10 -> """
            .-----------.
            |  Y Y Y Y  |
            |  Y Y Y Y  |
            |    Y Y    |
            |           |
            '-----------'
        """.trimIndent()
        11 -> """
            .-----------.
            |    .-.    |
            |   ( o )   |
            |    '+'    |
            |     Y     |
            |    / \    |
            '-----------'
        """.trimIndent()
        12 -> """
            .-----------.
            |   _/\     |
            |  /  Y \   |
            |  \    /   |
            |   |  /    |
            |   | /     |
            '-----------'
        """.trimIndent()
        13 -> """
            .-----------.
            |    .-.    |
            |   ( ^ )   |
            |  / Y-Y \  |
            |     |     |
            |    / \    |
            '-----------'
        """.trimIndent()
        14 -> """
            .-----------.
            |   .---.   |
            |  ( Y Y )  |
            |   '---'   |
            |    /|\    |
            |   / | \   |
            '-----------'
        """.trimIndent()
        else -> ""
    }

    /** U = a cup/chalice. Pip count for Ace-10, distinct figure art for court cards. */
    private fun cupGlyph(rank: Int): String = when (rank) {
        1 -> """
            .-----------.
            |     U     |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        2 -> """
            .-----------.
            |   U   U   |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        3 -> """
            .-----------.
            |  U  U  U  |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        4 -> """
            .-----------.
            |  U U U U  |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        5 -> """
            .-----------.
            |  U U U U  |
            |     U     |
            |           |
            '-----------'
        """.trimIndent()
        6 -> """
            .-----------.
            |  U U U U  |
            |    U U    |
            |           |
            '-----------'
        """.trimIndent()
        7 -> """
            .-----------.
            |  U U U U  |
            |   U U U   |
            |           |
            '-----------'
        """.trimIndent()
        8 -> """
            .-----------.
            |  U U U U  |
            |  U U U U  |
            |           |
            '-----------'
        """.trimIndent()
        9 -> """
            .-----------.
            |  U U U U  |
            |  U U U U  |
            |     U     |
            '-----------'
        """.trimIndent()
        10 -> """
            .-----------.
            |  U U U U  |
            |  U U U U  |
            |    U U    |
            '-----------'
        """.trimIndent()
        11 -> """
            .-----------.
            |    .-.    |
            |   ( o )   |
            |    '+'    |
            |     U     |
            |   '. .'   |
            '-----------'
        """.trimIndent()
        12 -> """
            .-----------.
            |   _/\     |
            |  /  U  \  |
            |  \     /  |
            |   |   /   |
            |   |  /    |
            '-----------'
        """.trimIndent()
        13 -> """
            .-----------.
            |    .-.    |
            |   ( ^ )   |
            |  / .U. \  |
            |     |     |
            |    / \    |
            '-----------'
        """.trimIndent()
        14 -> """
            .-----------.
            |   .---.   |
            |  ( ~U~ )  |
            |   '---'   |
            |    /|\    |
            |   / | \   |
            '-----------'
        """.trimIndent()
        else -> ""
    }

    /** | = a blade. Pip count for Ace-10, distinct figure art for court cards. */
    private fun swordGlyph(rank: Int): String = when (rank) {
        1 -> """
            .-----------.
            |     |     |
            |           |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        2 -> """
            .-----------.
            |    | |    |
            |           |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        3 -> """
            .-----------.
            |   | | |   |
            |           |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        4 -> """
            .-----------.
            |  | | | |  |
            |           |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        5 -> """
            .-----------.
            |  | | | |  |
            |     |     |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        6 -> """
            .-----------.
            |  | | | |  |
            |    | |    |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        7 -> """
            .-----------.
            |  | | | |  |
            |   | | |   |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        8 -> """
            .-----------.
            |  | | | |  |
            |  | | | |  |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        9 -> """
            .-----------.
            |  | | | |  |
            |  | | | |  |
            |     |     |
            |           |
            '-----------'
        """.trimIndent()
        10 -> """
            .-----------.
            |  | | | |  |
            |  | | | |  |
            |    | |    |
            |           |
            '-----------'
        """.trimIndent()
        11 -> """
            .-----------.
            |    .-.    |
            |   ( o )   |
            |    '+'    |
            |     |     |
            |     |     |
            '-----------'
        """.trimIndent()
        12 -> """
            .-----------.
            |   _/\     |
            |  / ||| \  |
            |  \ ||| /  |
            |   | | |   |
            |   | | |   |
            '-----------'
        """.trimIndent()
        13 -> """
            .-----------.
            |    .-.    |
            |   ( ^ )   |
            |    -+-    |
            |     |     |
            |     |     |
            '-----------'
        """.trimIndent()
        14 -> """
            .-----------.
            |   .---.   |
            |  (  X  )  |
            |   '---'   |
            |    -+-    |
            |     |     |
            '-----------'
        """.trimIndent()
        else -> ""
    }

    /** () = a coin/disc. Pip count for Ace-10, distinct figure art for court cards. */
    private fun pentGlyph(rank: Int): String = when (rank) {
        1 -> """
            .-----------.
            |     ()    |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        2 -> """
            .-----------.
            |   () ()   |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        3 -> """
            .-----------.
            |  () () () |
            |           |
            |           |
            '-----------'
        """.trimIndent()
        4 -> """
            .-----------.
            |() () () ()|
            |           |
            |           |
            '-----------'
        """.trimIndent()
        5 -> """
            .-----------.
            |() () () ()|
            |     ()    |
            |           |
            '-----------'
        """.trimIndent()
        6 -> """
            .-----------.
            |() () () ()|
            |   () ()   |
            |           |
            '-----------'
        """.trimIndent()
        7 -> """
            .-----------.
            |() () () ()|
            |  () () () |
            |           |
            '-----------'
        """.trimIndent()
        8 -> """
            .-----------.
            |() () () ()|
            |() () () ()|
            |           |
            '-----------'
        """.trimIndent()
        9 -> """
            .-----------.
            |() () () ()|
            |() () () ()|
            |     ()    |
            '-----------'
        """.trimIndent()
        10 -> """
            .-----------.
            |() () () ()|
            |() () () ()|
            |   () ()   |
            '-----------'
        """.trimIndent()
        11 -> """
            .-----------.
            |    .-.    |
            |   ( o )   |
            |    '+'    |
            |    (*)    |
            |     |     |
            '-----------'
        """.trimIndent()
        12 -> """
            .-----------.
            |   _/\     |
            |  /  ()  \ |
            |  \      / |
            |   |    |  |
            |   | () |  |
            '-----------'
        """.trimIndent()
        13 -> """
            .-----------.
            |    .-.    |
            |   ( ^ )   |
            |    (*)    |
            |     |     |
            |    / \    |
            '-----------'
        """.trimIndent()
        14 -> """
            .-----------.
            |   .---.   |
            |  ( (*) )  |
            |   '---'   |
            |    /|\    |
            |   / | \   |
            '-----------'
        """.trimIndent()
        else -> ""
    }

    private val majorArcana = listOf(
        TarotCard("The Fool", Arcana.MAJOR, """
                *
               /.\
              ( o )
               '='
              /| |\
             / | | \
            (  | |  )
             \ | | /
              \| |/
             __\_/__
            /  (0)  \
           |    |    |
           |   / \   |
            \_/   \_/
             |     |
             |     |
        """.trimIndent(),
            "New beginnings, a leap of faith, unlimited potential. Starting something without knowing exactly how it ends.",
            "Recklessness, hesitation at the wrong moment, or a leap taken without any preparation at all."),
        TarotCard("The Magician", Arcana.MAJOR, """
                 ^
                /|\
               / | \
              *--+--*
             /   |   \
            (    |    )
             \   |   /
              \  |  /
               \ | /
                \|/
               __V__
              |     |
              | INF |
              |_____|
                / \
               /   \
        """.trimIndent(),
            "Willpower, resourcefulness, turning ideas into action. You have everything you need already.",
            "Manipulation, untapped talent, or a gap between what you're capable of and what you're actually doing."),
        TarotCard("The High Priestess", Arcana.MAJOR, """
              .-'''-.
             /   o   \
            |  .---.  |
            | (     ) |
            |  '---'  |
            |    |    |
            | )     ( |
            |  \___/  |
            |    |    |
            '-.__|__.-'
               |   |
              (|   |)
               |   |
        """.trimIndent(),
            "Intuition, stillness, hidden knowledge waiting beneath the surface. Trust what you sense before you can explain it.",
            "Disconnection from your own instincts, secrets kept too long, or information withheld."),
        TarotCard("The Empress", Arcana.MAJOR, """
                 * *
              _,-'-,_
             /   *   \
            |  \___/  |
            |  /   \  |
             \ \___/ /
              '-,_,-'
               /   \
              /     \
             (   *   )
              \     /
               \___/
              /     \
             (   ()   )
        """.trimIndent(),
            "Abundance, nurturing, creativity given room to grow. Fertile ground for whatever you're building.",
            "Neglect -- of a project, a relationship, or yourself. Creative block from overextension."),
        TarotCard("The Emperor", Arcana.MAJOR, """
             .=========.
            /  # # # #  \
           |  #       #  |
           |  #  ###  #  |
           |  #  # #  #  |
           |  #  ###  #  |
           |  #       #  |
            \  # # # #  /
             '========='
               |     |
               |     |
              /|     |\
             ' |     | '
        """.trimIndent(),
            "Structure, authority, discipline that actually holds something up. Stability you built on purpose.",
            "Rigidity, control for its own sake, or structure that's become a cage."),
        TarotCard("The Hierophant", Arcana.MAJOR, """
                _____
               /     \
              |  +-+  |
              |  | |  |
              |  +-+  |
             /|       |\
            ( |  ---  | )
             \|       |/
              |  | |  |
              |  | |  |
             /|       |\
            ' |_______| '
        """.trimIndent(),
            "Tradition, guidance, learning from an established path rather than reinventing it.",
            "Rebellion against convention, or clinging to a rule that no longer serves the situation."),
        TarotCard("The Lovers", Arcana.MAJOR, """
              *         *
             /|\       /|\
            / | \     / | \
               |   X   |
               |  / \  |
             \ | /   \ | /
              \|/     \|/
               '       '
                \     /
                 \   /
                  \ /
                   V
        """.trimIndent(),
            "A real choice, alignment of values, a bond built on more than convenience.",
            "Misalignment, a choice avoided, or a relationship out of balance."),
        TarotCard("The Chariot", Arcana.MAJOR, """
              [==]---[==]
               |       |
              _|_______|_
             /           \
            |   /\   /\   |
            |  /  \ /  \  |
            |_/____V____\_|
                |     |
               ( O ) ( O )
                '-'   '-'
        """.trimIndent(),
            "Willpower in motion. Two opposing forces pulling the same direction because you made them.",
            "Losing control of the direction, or forcing momentum without a clear destination."),
        TarotCard("Strength", Arcana.MAJOR, """
                 .--..--.
                / .-''-. \
               / /      \ \
              ( (  o  o  ) )
               \ \  ==  / /
                \ '.__.' /
                 '-.__.-'
                /    |    \
               /     |     \
              (      |      )
               \    /|\    /
                \__/ | \__/
                     |
        """.trimIndent(),
            "Quiet resolve over brute force. Patience with something (or someone) difficult, including yourself.",
            "Self-doubt, or strength turned into forcing rather than steadying."),
        TarotCard("The Hermit", Arcana.MAJOR, """
                  .-.
                 ( * )
                  '+'
                   |
                .--'--.
               /   |    \
              |    |     |
              |    |     |
               \   |    /
                \  |   /
                 | | |
                /  |  \
               '   |   '
                  / \
        """.trimIndent(),
            "Withdrawal on purpose, seeking your own answer instead of the crowd's.",
            "Isolation past the point it was helping. Avoidance dressed up as solitude."),
        TarotCard("Wheel of Fortune", Arcana.MAJOR, """
                 _.--.._
               ,'        `.
              /   .------.  \
             |   /   /\   \  |
             |  <   <  >   >  |
             |   \   \/   /  |
              \   '------'  /
               `._        _,'
                  `------'
                 /        \
                '          '
        """.trimIndent(),
            "A turn in fortune, often out of your hands. What goes around genuinely comes around here.",
            "A cycle repeating because the lesson inside it hasn't landed yet."),
        TarotCard("Justice", Arcana.MAJOR, """
                   ___
                  |___|
                    |
              ______|______
             /      |      \
            o=======+=======o
            |               |
           [box]         [box]
                    |
                    |
                   / \
                  /   \
        """.trimIndent(),
            "Cause and consequence, clearly. A fair outcome, even if it isn't the one you wanted.",
            "An imbalance unaddressed, or accountability being dodged."),
        TarotCard("The Hanged Man", Arcana.MAJOR, """
              ----+----
                  |
                 /|\
                / | \
                  |
                (  o  )
                 \___/
                  | |
                 /   \
                |     |
                 \   /
                  | |
                  | |
        """.trimIndent(),
            "A deliberate pause. Seeing something differently because you stopped trying to force it.",
            "Stalling, martyrdom, or delay that's stopped being useful."),
        TarotCard("Death", Arcana.MAJOR, """
                 .---.
                ( o o )
                 |> <|
                  \_/
                 /   \
                |     |
                |     |
                |     |
                 \___/
                /     \
               |   |   |
               |   |   |
                '--+--'
        """.trimIndent(),
            "An ending that clears space. Rarely literal -- usually a chapter closing so the next can start.",
            "Resistance to a change that's already underway. Holding onto what's already gone."),
        TarotCard("Temperance", Arcana.MAJOR, """
                    *
                   /|\
                  / | \
                 /  |  \
                (   |   )
                 \  |  /
                  \_|_/
                 __| |__
                (       )
                 \     /
                  '-.-'
                    |
                   ( )
        """.trimIndent(),
            "Blending, patience, finding the middle path between two extremes.",
            "Excess, or overcorrection -- swinging too hard the other way."),
        TarotCard("The Devil", Arcana.MAJOR, """
               .-'''-.
              /  ^ ^  \
             | (o) (o) |
              \  ---  /
               '.___.'
                /|   |\
               / |   | \
              d  |   |  b
                 |   |
                (|   |)
                 |   |
                _|   |_
               (___) (___)
        """.trimIndent(),
            "A pattern you're aware of but haven't left. Naming it is the first real move.",
            "Breaking free, or finally seeing the chain you'd stopped noticing."),
        TarotCard("The Tower", Arcana.MAJOR, """
                    /\
                   /  \
                  / /\ \
                 /_/  \_\
                /|      |\
               / |      | \
              *  |      |  *
                 |      |
                _|      |_
               (          )
                \\      //
                 \\    //
                  \\  //
                   \\//
        """.trimIndent(),
            "Sudden collapse of something that wasn't as stable as it looked. Disruptive, but honest.",
            "Averting disaster narrowly, or fear of a collapse that hasn't actually happened yet."),
        TarotCard("The Star", Arcana.MAJOR, """
                    *
                   /|\
                  * | *
               \   \|/   /
                \   |   /
             * --+ --+-- +-- *
                /   |   \
               /   /|\   \
                  * | *
                   \|/
                    |
                   ( )
        """.trimIndent(),
            "Quiet hope after a hard stretch. Renewal that doesn't need to be loud to be real.",
            "Disconnection from hope, or faith that's grown hard to access."),
        TarotCard("The Moon", Arcana.MAJOR, """
                 .-'''-.
                /   ()  \
               |  ()     |
               |     ()  |
                \  ()   /
                 '-...-'
                /       \
               (  ^   ^  )
                \   V   /
                 '-...-'
                /|     |\
               ' |     | '
        """.trimIndent(),
            "Uncertainty, the unconscious, things that aren't fully visible yet. Trust the process more than the picture.",
            "Confusion clearing, or fear that turned out to be larger than the actual situation."),
        TarotCard("The Sun", Arcana.MAJOR, """
                \   |   /
               --\  |  /--
                  \ | /
              --    O    --
                  / | \
               --/  |  \--
                /   |   \
                   ( )
                  /   \
                 /     \
                (   *   )
        """.trimIndent(),
            "Clarity, vitality, plain good energy. Little translation needed here.",
            "Temporary clouding of something that's still fundamentally good."),
        TarotCard("Judgement", Arcana.MAJOR, """
                  /|\
                 / | \
                /  |  \
                   |
                .--+--.
               (   |   )
                '--+--'
                  /|\
                 / | \
                (  |  )
                 \_|_/
                  [_]
                 /   \
        """.trimIndent(),
            "A reckoning, a wake-up call, evaluating where you actually stand.",
            "Self-judgment too harsh to be useful, or avoiding an honest look."),
        TarotCard("The World", Arcana.MAJOR, """
                .-'''''-.
              ,'    o    ',
             /   .-----.   \
            |   /       \   |
            |  |    O    |  |
            |   \       /   |
             \   '-----'   /
              ',    o    ,'
                '-.....-'
                 /     \
                (   *   )
        """.trimIndent(),
            "Completion. A cycle closing well, with the lesson actually integrated.",
            "Something left unfinished, close to done but not quite landed.")
    )

    private val rankNames = listOf(
        "Ace", "Two", "Three", "Four", "Five", "Six", "Seven",
        "Eight", "Nine", "Ten", "Page", "Knight", "Queen", "King"
    )

    private fun minorCards(arcana: Arcana, glyphFn: (Int) -> String, suitWord: String,
                            themeUpright: List<String>, themeReversed: List<String>): List<TarotCard> =
        rankNames.mapIndexed { i, rank ->
            TarotCard(
                "$rank of $suitWord",
                arcana,
                glyphFn(i + 1),
                themeUpright[i],
                themeReversed[i]
            )
        }

    private val wands = minorCards(
        Arcana.WANDS, ::wandGlyph, "Wands",
        themeUpright = listOf(
            "A spark of inspiration, ready to act on.",
            "Planning ahead, weighing which direction to commit to.",
            "Momentum building after the initial push.",
            "A small, earned celebration -- pause and take it in.",
            "Friction from competing energy, not necessarily hostile.",
            "A win worth acknowledging, recognition earned.",
            "Holding your ground under pressure, and holding it well.",
            "Fast movement, news arriving quicker than expected.",
            "Tired but still standing, close to the finish.",
            "Carrying more than your share, worth checking if it's necessary.",
            "An eager beginner's energy, curious and untested.",
            "Bold, fast, sometimes impulsive action.",
            "Confident, warm leadership, sure of the direction.",
            "Visionary drive, bold leadership that inspires others."
        ),
        themeReversed = listOf(
            "A delay in getting started, or a false start.",
            "Fear of committing to the plan you've made.",
            "Frustration at a pace that's slower than expected.",
            "A celebration postponed or feeling hollow.",
            "Conflict that's become unproductive, going in circles.",
            "Recognition delayed, or won in a way that feels hollow.",
            "Feeling overwhelmed, defenses stretched thin.",
            "Delays, or news that's slower to arrive than hoped.",
            "Burnout, running on fumes near the end.",
            "Setting a burden down, or finally asking for help.",
            "Impulsiveness without follow-through.",
            "Recklessness, or all talk with no real motion.",
            "Impatience, or confidence that's tipped into stubbornness.",
            "Overreach, or a big vision losing touch with what's realistic."
        )
    )

    private val cups = minorCards(
        Arcana.CUPS, ::cupGlyph, "Cups",
        themeUpright = listOf(
            "An emotional opening, new feeling ready to be let in.",
            "A genuine connection, mutual and balanced.",
            "Shared joy, community, a reason to celebrate together.",
            "Mild discontent, or overlooking a good thing right in front of you.",
            "Grief over what's lost, but not the whole picture.",
            "Nostalgia, or reconnecting with something from the past.",
            "Too many appealing options, none of them fully examined yet.",
            "Walking away from something that no longer fits, even if it's hard.",
            "Contentment, satisfaction with how things actually are.",
            "Lasting happiness, harmony that isn't fragile.",
            "A gentle, curious emotional opening -- new feelings arriving softly.",
            "Following your heart with real, if idealistic, sincerity.",
            "Emotional maturity, compassion with clear boundaries.",
            "Calm emotional mastery, leading with warmth and control both."
        ),
        themeReversed = listOf(
            "Emotional blockage, or an opening that didn't take.",
            "A disconnect, or a bond that's grown one-sided.",
            "Overindulgence, or celebration that's turned excessive.",
            "Appreciating what's here again, after a stretch of taking it for granted.",
            "Moving past grief, or finally seeing what's still standing.",
            "Being stuck in the past instead of just visiting it.",
            "Clarity emerging from too many choices, a decision finally made.",
            "Staying too long in something already over.",
            "Overindulgence, or satisfaction that's become complacency.",
            "A happy foundation with a real crack in it worth addressing.",
            "Emotional immaturity, or creative energy that hasn't found its shape.",
            "Moodiness, or idealism that's curdled into disappointment.",
            "Emotional overwhelm, or compassion with no boundary left.",
            "Emotional manipulation, or moods that are hard to read and harder to trust."
        )
    )

    private val swords = minorCards(
        Arcana.SWORDS, ::swordGlyph, "Swords",
        themeUpright = listOf(
            "A clear-eyed breakthrough, mental clarity cutting through noise.",
            "A standoff, avoiding a decision by staying perfectly still.",
            "A painful but honest truth, better out than buried.",
            "A deliberate rest after conflict, recovery time.",
            "A hollow win, technically ahead but at real cost.",
            "Moving away from a difficult situation toward calmer water.",
            "A quiet, maybe underhanded maneuver -- worth examining your own methods.",
            "Feeling trapped by a situation that has more exits than it looks like.",
            "Anxiety keeping you up, worry louder than the actual facts.",
            "A hard bottom, painful but with nowhere left to fall.",
            "Sharp perception, but not much tact yet.",
            "Fast, decisive thinking, direct to the point of bluntness.",
            "Sharp, clear communication, sometimes cold.",
            "Strategic, incisive thinking, a mind that cuts to the core of things."
        ),
        themeReversed = listOf(
            "Confusion, or a breakthrough that hasn't fully landed yet.",
            "The standoff finally breaking, a decision made either way.",
            "Truth surfacing on its own terms, in its own time.",
            "Restlessness -- rest interrupted before it did its job.",
            "Reconciliation, or letting go of a fight not worth winning.",
            "Struggling to leave a difficult patch, resisting the exit.",
            "Getting caught, or a strategy that backfires.",
            "Freeing yourself from a trap partly of your own making.",
            "Relief after anxiety, or facing the worry instead of avoiding it.",
            "Recovery beginning, however slowly, after the worst has passed.",
            "Gossip, or sharp words used carelessly.",
            "Impulsive words or actions that outrun the thinking.",
            "Harsh honesty tipping into cruelty.",
            "Cold, manipulative use of intellect over honesty."
        )
    )

    private val pentacles = minorCards(
        Arcana.PENTACLES, ::pentGlyph, "Pentacles",
        themeUpright = listOf(
            "A new opportunity, practical and worth taking seriously.",
            "Balancing more than one priority, actively and consciously.",
            "Collaboration, skill combined with others' toward something solid.",
            "Holding tightly to security, maybe too tightly.",
            "A hard patch, materially or otherwise -- support is closer than it looks.",
            "Generosity, giving or receiving fairly.",
            "Patience with a long-term investment, tending without rushing.",
            "Focused, steady craftsmanship, improving through repetition.",
            "Self-sufficiency, comfort earned on your own terms.",
            "Lasting stability, something built to outlast you.",
            "A grounded, curious start toward something practical.",
            "Methodical, reliable effort, unglamorous but real.",
            "Practical warmth, nurturing through steady care.",
            "Solid, earned security, mastery through discipline."
        ),
        themeReversed = listOf(
            "A missed opportunity, or one worth a second look before dismissing it.",
            "Disorganization, priorities out of balance.",
            "Misalignment in a shared effort, or working at cross purposes.",
            "Loosening a grip that's become more about fear than security.",
            "Support finally arriving, or realizing it was there all along.",
            "An imbalance in giving and receiving worth naming.",
            "Impatience with a slow return, or reassessing the investment.",
            "Perfectionism, or skill without real progress behind it.",
            "Overwork in the name of self-sufficiency.",
            "A foundation cracking, or wealth that isn't the security it looked like.",
            "A practical start that stalled before it really began.",
            "Rigidity, or diligence that's become mechanical.",
            "Smothering under the guise of care, or neglect of practical needs.",
            "Overvaluing material security, or generosity turned controlling."
        )
    )

    val fullDeck: List<TarotCard> = majorArcana + wands + cups + swords + pentacles

    fun pullOne(): TarotCard = fullDeck.random()

    fun pullOneWithOrientation(): Pair<TarotCard, Boolean> {
        val card = pullOne()
        val reversed = (0..1).random() == 0
        return card to reversed
    }

    /** Draws [count] distinct cards (no repeats within a reading), each with a
     *  random orientation. Order matches spread position order. */
    fun pull(count: Int): List<Pair<TarotCard, Boolean>> =
        fullDeck.shuffled().take(count).map { it to ((0..1).random() == 0) }

    val spreads: List<TarotSpread> = listOf(
        TarotSpread("SINGLE", "SINGLE CARD", listOf("The Card")),
        TarotSpread("THREE", "PAST / PRESENT / FUTURE", listOf("Past", "Present", "Future")),
        TarotSpread("SOA", "SITUATION / OBSTACLE / ADVICE", listOf("Situation", "Obstacle", "Advice")),
        TarotSpread(
            "CELTIC", "CELTIC CROSS",
            listOf(
                "1. Present", "2. Challenge", "3. Foundation", "4. Recent Past", "5. Crown",
                "6. Near Future", "7. Self", "8. Environment", "9. Hopes & Fears", "10. Outcome"
            )
        )
    )

    fun spreadById(id: String): TarotSpread = spreads.firstOrNull { it.id == id } ?: spreads.first()
}

/** A named layout: one label per card drawn, in draw order. Stored in the log
 *  by [id] and [positionLabel], so renaming a spread later doesn't break history. */
data class TarotSpread(val id: String, val name: String, val positions: List<String>)
