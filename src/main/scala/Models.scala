object Models {
    case class AnimeEpisodePlayerSorl( playerName: String, url: String )

    case class AnimeEpisodeSorl( title: String, url: String, players: Array[ AnimeEpisodePlayerSorl ] )
    case class AnimeSorl( id: UUID, title: String, url: String, imageUrl: Option[ String ] = None, eps: Array[ AnimeEpisodeSorl ] )


}
