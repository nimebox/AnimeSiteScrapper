import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors

import Models.{AnimePage, AnimePageEpisode, AnimePagePlayer}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import sttp.client._
import org.jsoup.Connection
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class AnimeDesuPL( ) {

    implicit val context: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService( Executors.newSingleThreadExecutor() )

    private val browser = new JsoupBrowser( userAgent = Const.ua ) {
        override def requestSettings( conn: Connection ): Connection = conn.timeout( 60000 )
    }

    implicit val backend: SttpBackend[ Identity, Nothing, NothingT ] = HttpURLConnectionBackend()

    browser.setCookie( "animedesu.pl", "age_gate", "18" )

    private val solrImpl = new SolrImpl( "ad" )

    def updateAnimeDB( ): Unit = {
        getAnimeList.foreach { anime =>
            val episodesAndImage = getAnimeEpisodes( anime )
            val animePageObj = anime.copy( imageB64 = episodesAndImage._2 )
            Utils.randomTimeout()
            val episodes = episodesAndImage._1.map { epb =>
                val newObj = epb.copy( players = getEpisodePlayers( epb ) )
                Utils.randomTimeout()
                newObj
            }.toSet
            solrImpl.updateAnime( animePageObj, episodes )
        }
    }

    def getAnimeList: Array[ AnimePage ] = {
        val ret = ArrayBuffer[ AnimePage ]()
        println( "Getting data" )

        val animeList = browser.get( "https://animedesu.pl/anime/list-mode/" )

        ret.addAll( ( animeList >> elementList( ".soralist .blix ul li a" ) ).map { el =>
            AnimePage( el >> text, el >> attr( "href" ) )
        } )
        ret.toArray
    }

    def getAnimeEpisodes( animePage: AnimePage ): (Array[ AnimePageEpisode ], Option[ String ]) = {
        val ret = ArrayBuffer[ AnimePageEpisode ]()
        println( s"Getting episodes for ${animePage.title}" )

        val episodeList = browser.get( animePage.url )

        ret.addAll( ( episodeList >> elementList( ".epcheck .eplister ul li a" ) ).map { el =>
            AnimePageEpisode( el >> element( ".epl-title" ) >> text, el >> attr( "href" ), Set.empty )
        } )

        val imageUrl = episodeList >> element( ".thumbook .thumb img" ) >> attr( "src" )
        val image = downloadImage( imageUrl, animePage.url )

        (ret.toArray, image)
    }

    def getEpisodePlayers( animeEpisode: AnimePageEpisode ): Set[ AnimePagePlayer ] = {
        val ret = scala.collection.mutable.Set[ AnimePagePlayer ]()
        println( s"Getting players for ${animeEpisode.title}" )

        val playersList = browser.get( animeEpisode.url )

        ( playersList >> elementList( ".video-nav .mobius select option" ) ).foreach { el =>
            val title = el >> text
            val hash = el >> attr( "value" )

            if ( !hash.isBlank ) {
                val decodedIframeString = new String( Base64.getDecoder.decode( hash ), StandardCharsets.UTF_8 )

                val url = browser.parseString( decodedIframeString ).body >> element( "iframe" ) >> attr( "src" )

                ret.addOne( AnimePagePlayer( title, url ) )
            }
        }

        ret.toSet
    }

    def downloadImage( url: String, refererUrl: String ): Option[ String ] = {
        val request = basicRequest
            .header( "User-Agent", Const.ua )
            .header( "Referer", refererUrl )
            .get( uri"$url" )
            .response( asByteArray )

        val response = request.send()

        if ( response.is200 ) {
            response.body.fold( l => None, r => Some( Base64.getEncoder.encodeToString( r ) ) )
        } else {
            None
        }
    }
}
