import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

import Models.{Anime, AnimeEpisode, AnimePage, AnimePageEpisode, AnimePlayer, AnimeSorl}
import io.ino.solrs.AsyncSolrClient
import io.ino.solrs.future.ScalaFutureFactory.Implicit
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.SolrInputDocument

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}

class SolrImpl( val serviceName: String ) {
    implicit val context: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService( Executors.newSingleThreadExecutor() )

    object EntryType extends Enumeration {
        // type EntryType = Value
        val ANIME, EPISODE, PLAYER = Value
    }

    private val solrService = AsyncSolrClient( s"http://localhost:8984/solr/$serviceName" )

    private def removeAnime( anime: Anime ): Unit = {

    }

    private def saveAnime( animePage: AnimePage, episodes: Set[ AnimePageEpisode ] ): Unit = {
        val anime = Anime( id = UUID.randomUUID(),
            title = animePage.title,
            url = animePage.url,
            imageB64 = animePage.imageB64.fold( "" )( i => i ),
            episodes = episodes.map { ep =>
                AnimeEpisode(
                    id = UUID.randomUUID(),
                    title = ep.title,
                    url = ep.url,
                    players = ep.players.map { player =>
                        AnimePlayer( id = UUID.randomUUID(),
                            title = player.title,
                            url = player.url )
                    } )
            }, updated = ZonedDateTime.now() )

        saveAnime( anime )
    }

    private def saveAnime( anime: Anime ): Unit = {
        val animeDoc = new SolrInputDocument()
        animeDoc.addField( "id", anime.id.toString )
        animeDoc.addField( "type", EntryType.ANIME.id )
        animeDoc.addField( "title", anime.title )
        animeDoc.addField( "imageB64", anime.imageB64 )
        animeDoc.addField( "url", anime.url )
        animeDoc.addField( "updated", ZonedDateTime.now().format( DateTimeFormatter.ISO_INSTANT ) )

        val episodes = new ArrayBuffer[ SolrInputDocument ]()
        val players = new ArrayBuffer[ SolrInputDocument ]()

        anime.episodes.foreach { ep =>
            val episodeDoc = new SolrInputDocument()
            episodeDoc.addField( "id", ep.id.toString )
            episodeDoc.addField( "animeId", anime.id.toString )
            episodeDoc.addField( "type", EntryType.EPISODE.id )
            episodeDoc.addField( "title", ep.title )
            episodeDoc.addField( "url", ep.url )
            episodeDoc.addField( "updated", ZonedDateTime.now().format( DateTimeFormatter.ISO_INSTANT ) )

            ep.players.foreach { pl =>
                val playerDoc = new SolrInputDocument()
                playerDoc.addField( "id", pl.id.toString )
                playerDoc.addField( "animeId", anime.id.toString )
                playerDoc.addField( "episodeId", ep.id.toString )
                playerDoc.addField( "type", EntryType.EPISODE.id )
                playerDoc.addField( "title", pl.title )
                playerDoc.addField( "url", pl.url )
                playerDoc.addField( "updated", ZonedDateTime.now().format( DateTimeFormatter.ISO_INSTANT ) )
                players.addOne( playerDoc )
            }

            episodes.addOne( episodeDoc )
        }

        val docToUpdate = ArrayBuffer[ SolrInputDocument ]()
        docToUpdate.addOne( animeDoc )
        docToUpdate.addAll( episodes )
        docToUpdate.addAll( players )

        val dbAdd = for {
            _ <- solrService.addDocs( docs = docToUpdate )
            _ <- solrService.commit()
        } yield print( s"${anime.title} added\n" )

        Await.result( dbAdd, Duration.Inf )
    }

    def updateAnime( animePage: AnimePage, episodes: Set[ AnimePageEpisode ] ): Unit = {
        val animeDB = getDataByUrl( animePage.url )

        if ( animeDB.isEmpty ) {
            saveAnime( animePage, episodes )
            return
        }

        //TODO!!!!!


    }

    def getDataByUrl( url: String ): Option[ Anime ] = {
        val animeEntryResult = Await.result( solrService.query(
            new SolrQuery( s"sourceUrl:*$url AND type:${EntryType.ANIME.id}" ).setRows( 1 )
        ), Duration.Inf ).getResults

        if ( animeEntryResult.isEmpty ) {
            return None
        }

        val animeEntry = animeEntryResult.get( 0 )

        val animeId = UUID.fromString( animeEntry.getFieldValue( "id" ).toString )
        val animeTitle = animeEntry.getFieldValue( "title" ).toString
        val animeUrl = animeEntry.getFieldValue( "url" ).toString
        val animeImage = animeEntry.getFieldValue( "imageB64" ).toString
        val animeUpdated = ZonedDateTime.parse( animeEntry.getFieldValue( "updated" ).toString, DateTimeFormatter.ISO_INSTANT )

        val animeEpisodesResult = Await.result( solrService.query(
            new SolrQuery( s"animeId:${animeId.toString} AND type:${EntryType.EPISODE.id}" )
        ), Duration.Inf ).getResults

        if ( animeEpisodesResult.isEmpty ) {
            return Some( Anime( animeId, title = animeTitle, url = animeUrl, imageB64 = animeImage, episodes = Set.empty, updated = animeUpdated ) )
        }

        val epTmp: ArrayBuffer[ AnimeEpisode ] = ArrayBuffer.empty

        animeEpisodesResult.forEach { epf =>
            val episodeId = UUID.fromString( epf.getFieldValue( "id" ).toString )
            val episodeTitle = epf.getFieldValue( "title" ).toString
            val episodeUrl = epf.getFieldValue( "url" ).toString

            val playersSet: scala.collection.mutable.Set[ AnimePlayer ] = scala.collection.mutable.Set.empty

            Await.result( solrService.query(
                new SolrQuery( s"episodeId:(${
                    epTmp.flatten {
                        _.id.toString
                    }.mkString( " " )
                }) AND type:${EntryType.PLAYER.id}" )
            ), Duration.Inf ).getResults.forEach { plr =>
                val playerId = UUID.fromString( epf.getFieldValue( "id" ).toString )
                val playerTitle = epf.getFieldValue( "title" ).toString
                val playerUrl = epf.getFieldValue( "url" ).toString

                playersSet.addOne( AnimePlayer( playerId, playerTitle, playerUrl ) )
            }

            epTmp.addOne( AnimeEpisode( episodeId, title = episodeTitle, url = episodeUrl, players = playersSet.toSet ) )
        }

        None
    }

}
