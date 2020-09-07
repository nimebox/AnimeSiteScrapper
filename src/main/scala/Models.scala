object Models {
    case class AnimeEpisodePlayerSorl( playerName: String, url: String )

    case class AnimeEpisodeSorl( title: String, url: String, players: Array[ AnimeEpisodePlayerSorl ] )

    case class AnimeSorl( title: String, url: String, eps: Array[ AnimeEpisodeSorl ] )

}
