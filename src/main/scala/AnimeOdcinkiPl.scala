
import java.util.Base64

import Models.{AnimePage, AnimePageEpisode, AnimePagePlayer}
import com.google.gson.GsonBuilder
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import org.apache.commons.codec.binary.Hex
import org.jsoup.{Connection, HttpStatusException}
import sttp.client._
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}

import scala.collection.mutable.ArrayBuffer

class AnimeOdcinkiPl( ) {
    case class DataHash( a: String, b: String, v: String )

    private val key = "s05z9Gpd=syG^7{"

    private val browser = new JsoupBrowser( userAgent = Const.ua ) {
        override def requestSettings( conn: Connection ): Connection = conn.timeout( 60000 )
    }

    implicit val backend: SttpBackend[ Identity, Nothing, NothingT ] = HttpURLConnectionBackend()

    private val gson = new GsonBuilder().create()

    private val solrImpl = new SolrImpl( "ao" )

    def updateAnimeDB( ): Unit = {
        getAnimeList.foreach { anime =>
            val episodesAndImage = getAnimeEpisodes( anime )
            if ( episodesAndImage.isDefined ) {
                val animePageObj = anime.copy( imageB64 = episodesAndImage.get._2 )
                Utils.randomTimeout()
                val episodes = episodesAndImage.get._1.map { epb =>
                    val newObj = epb.copy( players = getEpisodePlayers( epb ) )
                    Utils.randomTimeout()
                    newObj
                }.toSet
                solrImpl.updateAnime( animePageObj, episodes )
            } else {
                Utils.randomTimeout()
            }
        }
    }

    def getAnimeList: Array[ AnimePage ] = {
        val ret = ArrayBuffer[ AnimePage ]()
        println( "[AO] Getting data" )

        val tvSeries = browser.get( "https://anime-odcinki.pl/anime" )
        ret.addAll( ( tvSeries >> elementList( ".views-table tbody tr" ) ).map { item =>
            val urlItem = item >> element( "td a" )
            AnimePage( urlItem >> text, urlItem >> attr( "href" ) )
        } )

        val movieSeries = browser.get( "https://anime-odcinki.pl/filmy" )
        ret.addAll( ( movieSeries >> elementList( ".views-table tbody tr" ) ).map { item =>
            val urlItem = item >> element( "td a" )
            AnimePage( urlItem >> text, urlItem >> attr( "href" ) )
        } )

        ret.toArray
    }

    def getAnimeEpisodes( ap: AnimePage ): Option[ (Array[ AnimePageEpisode ], Option[ String ]) ] = {
        var canRetry = true
        var ret: Option[ (Array[ AnimePageEpisode ], Option[ String ]) ] = None
        println( s"[AO] Getting episodes for ${ap.title}" )

        while ( canRetry ) {
            try {
                val animePage = browser.get( ap.url )
                val image = downloadImage( animePage >> element( "#anime-header div div div img" ) >> attr( "src" ), ap.url )
                ret = Some( ( animePage >> elementList( ".view-lista-odcink-w div ul li" ) ).map { item =>
                    val urlItem = item >> element( "a" )
                    val url = urlItem >> attr( "href" )
                    AnimePageEpisode( urlItem >> text, url, Set.empty )
                }.toArray, image )

                val savedAnime = solrImpl.getMinimalDataByUrl( ap.url )

                if ( savedAnime.isDefined ) {
                    if(savedAnime.get.episodesCount <= ret.get._1.length) {
                        return None
                    }
                }

                canRetry = false

            } catch {
                case e: Exception =>
                    e.printStackTrace()
                    println( s"[AO] Retry getting episodes of ${ap.title}" )
            }
            Utils.randomTimeout()
        }

        ret
    }

    def getEpisodePlayers( aep: AnimePageEpisode ): Set[ AnimePagePlayer ] = {
        var ret = Set[ AnimePagePlayer ]()
        var canRetry = true

        while ( canRetry ) {
            try {
                val animeEpisodePage = browser.get( aep.url )
                ret = ( animeEpisodePage >> elementList( "#video-player-control div" ) ).map { item =>
                    val playerName = item >> text
                    val dataHash = item >> attr( "data-hash" )
                    val dataHashObj = gson.fromJson( dataHash, classOf[ DataHash ] )
                    val url = AESHelper.decrypt( key, Hex.decodeHex( dataHashObj.v ), dataHashObj.a )
                    AnimePagePlayer( playerName, url = url.replace( """\/""", "/" ).replace( "\"", "" ) )
                }.toSet
                canRetry = false
            } catch {
                case e: HttpStatusException =>
                    if ( e.getStatusCode == 404 ) {
                        println( s"[AO] Page: ${aep.title} not exist. Skipping." )
                        canRetry = false
                    }
                case e: Exception =>
                    e.printStackTrace()
                    println( s"[AO] Retry getting players of ${aep.title}" )
            }
            Utils.randomTimeout()
        }

        ret
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
