import java.util.UUID

object Models {
    case class AnimePage( title: String, url: String, imageB64: Option[ String ] = None )

    case class AnimePageEpisode( title: String, url: String, players: Set[ AnimePagePlayer ] )

    case class AnimePagePlayer( title: String, url: String )


    case class AnimePlayer( id: UUID, title: String, url: String )

    case class AnimeEpisode( id: UUID, title: String, url: String, players: Set[ AnimePlayer ] )

    case class Anime( id: UUID, title: String, url: String, imageB64: String, episodes: Set[ AnimeEpisode ] )

    case class MinimalAnimeObj( id: UUID, title: String, url: String, episodesCount: Int )

}
