import java.time.ZonedDateTime
import java.util.UUID

import org.apache.solr.common.SolrInputDocument

object Models {

    case class AnimeEpisodePlayerSorl( playerName: String, url: String ) {
        def toSolrDocument( episodeId: UUID ): SolrInputDocument = {
            val doc = new SolrInputDocument()
            doc.addField( "id", UUID.randomUUID().toString )
            doc.addField( "episodeId", episodeId.toString )
            doc.addField( "playerName", playerName )
            doc.addField( "url", url )
            doc
        }
    }

    case class AnimeEpisodeSorl( title: String, url: String, players: Array[ AnimeEpisodePlayerSorl ] ) {
        def toSolrDocument( animeId: UUID ): (SolrInputDocument, UUID) = {
            val doc = new SolrInputDocument()
            val epId = UUID.randomUUID()
            doc.addField( "id", epId.toString )
            doc.addField( "animeId", animeId.toString )
            doc.addField( "title", title )
            doc.addField( "url", url )
            (doc, epId)
        }
    }

    case class AnimeSorl( id: UUID, title: String, url: String, imageUrl: Option[ String ] = None, eps: Array[ AnimeEpisodeSorl ] )

    case class AnimePage( title: String, url: String, imageB64: Option[ String ] = None )

    case class AnimePageEpisode( title: String, url: String, players: Set[ AnimePagePlayer ] )

    case class AnimePagePlayer( title: String, url: String )


    case class AnimePlayer( id: UUID, title: String, url: String )

    case class AnimeEpisode( id: UUID, title: String, url: String, players: Set[ AnimePlayer ] )

    case class Anime( id: UUID, title: String, url: String, imageB64: String, episodes: Set[ AnimeEpisode ], updated: ZonedDateTime )

}
