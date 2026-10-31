package org.njarasoa.fijerena.core.network.sports

/**
 * The word lists and alias tables of [GameChannelMatcher]. Every entry is folded
 * ([SportsText.fold]: lower case, no accents, no apostrophes, "+" as "plus"). Add a name form here
 * when a guide spells a team, a competition or a channel in a way the rules do not find.
 */
internal object SportsAliases {
    /** One-word names that are too common to name a team alone ("Inter", "City", "Heat"). */
    val COMMON_WORDS =
        setOf(
            "inter",
            "city",
            "united",
            "utd",
            "real",
            "athletic",
            "atletico",
            "sporting",
            "olympique",
            "racing",
            "rangers",
            "rovers",
            "wanderers",
            "albion",
            "county",
            "town",
            "club",
            "sport",
            "sports",
            "deportivo",
            "union",
            "dynamo",
            "dinamo",
            "red",
            "star",
            "stars",
            "young",
            "boys",
            "national",
            "nacional",
            "milan",
            "madrid",
            "paris",
            "london",
            "manchester",
            "sheffield",
            "bristol",
            "birmingham",
            "nottingham",
            "palace",
            "villa",
            "forest",
            "hotspur",
            "wednesday",
            "heat",
            "magic",
            "thunder",
            "jazz",
            "kings",
            "wild",
            "lightning",
            "fire",
            "storm",
            "fever",
            "sky",
            "sun",
            "suns",
            "sparks",
            "liberty",
            "dream",
            "mercury",
            "wings",
            "lynx",
            "aces",
            "giants",
            "jets",
            "panthers",
            "cardinals",
            "royals",
            "nationals",
            "saints",
            "lions",
            "tigers",
            "eagles",
            "bears",
            "america",
            "nice",
            "pirates",
        )

    /** Club words that come before or after a club's name ("FC", "AFC", "SV", "Handball"). */
    val CLUB_WORDS =
        setOf(
            "fc",
            "cf",
            "sc",
            "afc",
            "ac",
            "as",
            "ss",
            "ssc",
            "fk",
            "sk",
            "nk",
            "if",
            "bk",
            "sv",
            "vfb",
            "vfl",
            "tsg",
            "cd",
            "ud",
            "sd",
            "ca",
            "rc",
            "us",
            "club",
            "de",
            "handball",
            "hb",
            "basketball",
            "basket",
            "football",
        )

    /** A city's long name and the short one guides use in team names. */
    val CITY_SHORT =
        listOf(
            "los angeles" to "la",
            "new york" to "ny",
            "san francisco" to "sf",
            "tampa bay" to "tb",
            "kansas city" to "kc",
            "oklahoma city" to "okc",
            "new england" to "ne",
        )

    /** Groups of names of one team. A name found in a group brings all the others. */
    private val TEAM_GROUPS =
        listOf(
            // England
            setOf("manchester united", "man utd", "man united", "man u", "manu"),
            setOf("manchester city", "man city"),
            setOf("tottenham hotspur", "tottenham", "spurs"),
            setOf("wolverhampton wanderers", "wolverhampton", "wolves"),
            setOf("brighton and hove albion", "brighton"),
            setOf("nottingham forest", "nottm forest", "n forest"),
            setOf("newcastle united", "newcastle"),
            setOf("west ham united", "west ham"),
            setOf("sheffield united", "sheff utd", "sheffield utd", "sheff united"),
            setOf("sheffield wednesday", "sheff wed", "sheff wednesday"),
            setOf("bournemouth", "bmouth"),
            setOf("sunderland", "sundland"),
            setOf("blackburn rovers", "blackburn", "bburn"),
            setOf("queens park rangers", "qpr"),
            setOf("west bromwich albion", "west brom", "wba"),
            setOf("leicester city", "leicester"),
            setOf("leeds united", "leeds"),
            setOf("preston north end", "preston ne", "preston"),
            // France
            setOf("paris saint germain", "paris sg", "psg"),
            setOf("olympique de marseille", "olympique marseille", "marseille", "om"),
            setOf("olympique lyonnais", "lyon", "ol"),
            setOf("losc lille", "lille", "losc"),
            setOf("as monaco", "monaco"),
            setOf("ogc nice", "nice"),
            setOf("stade rennais", "rennes"),
            // Germany
            setOf("bayern munich", "bayern munchen", "fc bayern", "bayern"),
            setOf("borussia dortmund", "dortmund", "bvb"),
            setOf("borussia monchengladbach", "monchengladbach", "gladbach"),
            setOf("bayer leverkusen", "leverkusen"),
            setOf("rb leipzig", "leipzig"),
            setOf("eintracht frankfurt", "frankfurt"),
            setOf("thw kiel", "kiel"),
            setOf("sg flensburg handewitt", "flensburg handewitt", "flensburg"),
            setOf("fuchse berlin", "fuechse berlin"),
            // Italy, Spain, Portugal, the Netherlands
            setOf("internazionale", "inter milan", "inter"),
            setOf("ac milan", "milan"),
            setOf("juventus", "juve"),
            setOf("atletico madrid", "atletico de madrid", "atleti"),
            setOf("barcelona", "barca", "fc barcelona"),
            setOf("athletic club", "athletic bilbao", "bilbao"),
            setOf("villarreal", "vreal"),
            setOf("sporting cp", "sporting lisbon", "sporting clube de portugal"),
            setOf("benfica", "sl benfica"),
            setOf("psv eindhoven", "psv"),
            // South Africa
            setOf("kaizer chiefs", "amakhosi"),
            setOf("orlando pirates", "pirates"),
            setOf("mamelodi sundowns", "sundowns", "masandawana"),
            // United States
            setOf("philadelphia 76ers", "76ers", "sixers"),
            setOf("portland trail blazers", "trail blazers", "blazers"),
            setOf("san francisco 49ers", "49ers", "niners"),
        )

    private val TEAM_INDEX: Map<String, Set<String>> = TEAM_GROUPS.flatMap { g -> g.map { it to g } }.toMap()

    fun teamGroup(name: String): Set<String>? = TEAM_INDEX[name]

    /** Words before a competition's name that guides leave out ("English Premier League", "UEFA Europa League"). */
    val COMPETITION_PREFIXES =
        setOf(
            "english",
            "spanish",
            "italian",
            "german",
            "french",
            "portuguese",
            "dutch",
            "scottish",
            "turkish",
            "belgian",
            "mexican",
            "brazilian",
            "argentine",
            "argentinian",
            "american",
            "saudi",
            "south",
            "african",
            "uefa",
            "fifa",
            "conmebol",
            "concacaf",
            "caf",
            "ehf",
        )

    /** Groups of names of one competition, in the languages of the guides. */
    private val COMPETITION_GROUPS =
        listOf(
            setOf("premier league", "epl", "pl", "barclays premier league"),
            setOf("champions league", "ucl", "ligue des champions", "liga de campeones", "champions league uefa"),
            setOf("europa league", "uel", "ligue europa"),
            setOf("conference league", "uecl", "europa conference league", "ligue europa conference"),
            setOf("laliga", "la liga"),
            setOf("bundesliga", "1 bundesliga"),
            setOf("2 bundesliga", "zweite bundesliga"),
            setOf("ligue 1", "ligue1"),
            setOf("efl championship", "championship", "sky bet championship"),
            setOf("efl cup", "carabao cup", "league cup"),
            setOf("nfl", "national football league"),
            setOf("nba", "national basketball association"),
            setOf("premiership", "betway premiership", "betway prem", "dstv premiership", "psl"),
            setOf("united rugby championship", "urc"),
        )

    private val COMPETITION_INDEX: Map<String, Set<String>> = COMPETITION_GROUPS.flatMap { g -> g.map { it to g } }.toMap()

    fun competitionGroup(name: String): Set<String>? = COMPETITION_INDEX[name]

    /** Title words of a programme or a channel name that is not the live game. "(R)" is read apart ([GameText.isReplay]). */
    val REPLAY_WORDS =
        setOf(
            "highlights",
            "hlts",
            "hl",
            "replay",
            "rerun",
            "rewind",
            "classic",
            "classics",
            "retro",
            "review",
            "kompakt",
            "zusammenfassung",
            "resume",
            "resumen",
            "rediffusion",
            "rediff",
            "re air",
            "reair",
            "wiederholung",
            "delayed",
            "encore",
            "reprise",
            "repeticion",
            "debrief",
            "termine",
            "flashback",
            "best of",
            "temps forts",
            "alle tore",
            "next game",
            "match of the day",
            "goals of",
        )

    /** Title words of a programme before or around the game, kept only when nothing better is on the channel. */
    val FILLER_WORDS =
        setOf(
            "a venir",
            "coming up",
            "avant match",
            "pre game",
            "pregame",
            "preview",
            "build up",
            "countdown",
            "warm up",
            "post game",
            "postgame",
            "apres match",
            "possible overtime",
        )

    /** Title words of a live broadcast. */
    val LIVE_WORDS = setOf("live", "en direct", "direct", "en vivo", "ao vivo", "directo", "diretta")
}
