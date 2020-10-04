import java.nio.charset.StandardCharsets
import java.util.Base64

import Models.{AnimePage, AnimePageEpisode, AnimePagePlayer}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import sttp.client._
import org.jsoup.Connection
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}

import scala.collection.mutable.ArrayBuffer

class AnimeDesuPL( ) {

    private val browser = new JsoupBrowser( userAgent = Const.ua ) {
        override def requestSettings( conn: Connection ): Connection = conn.timeout( 60000 )
    }

    implicit val backend: SttpBackend[ Identity, Nothing, NothingT ] = HttpURLConnectionBackend()

    browser.setCookie( "animedesu.pl", "age_gate", "18" )

    private val solrImpl = new SolrImpl( "ad" )

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
        println( "[AD] Getting data" )

        val animeList = browser.get( "https://animedesu.pl/anime/list-mode/" )

        ret.addAll( ( animeList >> elementList( ".soralist .blix ul li a" ) ).map { el =>
            AnimePage( el >> text, el >> attr( "href" ) )
        } )
        ret.toArray
    }

    def getAnimeEpisodes( ap: AnimePage ): Option[ (Array[ AnimePageEpisode ], Option[ String ]) ] = {
        var canRetry = true
        var ret: Option[ (Array[ AnimePageEpisode ], Option[ String ]) ] = None
        println( s"[AD] Getting episodes for ${ap.title}" )

        while ( canRetry ) {
            try {
                val animePage = browser.get( ap.url )
                val image = downloadImage( animePage >> element( ".thumbook .thumb img" ) >> attr( "src" ), ap.url )

                ret = Some( (( animePage >> elementList( ".epcheck .eplister ul li a" ) ).map { item =>
                    AnimePageEpisode( item >> element( ".epl-title" ) >> text, item >> attr( "href" ), Set.empty )
                }.toArray, image) )

                val savedAnime = solrImpl.getMinimalDataByUrl( ap.url )

                if ( savedAnime.isDefined ) {
                    ret.get._1.length
                    if ( savedAnime.get.episodesCount <= ret.get._1.length ) {
                        return None
                    }
                }
                canRetry = false
            } catch {
                case e: Exception =>
                    e.printStackTrace()
                    println( s"[AD] Retry getting episodes of ${ap.title}" )
            }
            Utils.randomTimeout()
        }

        ret
    }

    def getEpisodePlayers( animeEpisode: AnimePageEpisode ): Set[ AnimePagePlayer ] = {
        val ret = scala.collection.mutable.Set[ AnimePagePlayer ]()
        //println( s"[AD] Getting players for ${animeEpisode.title}" )

        val playersList = browser.get( animeEpisode.url )

        ( playersList >> elementList( ".video-nav .mobius select option" ) ).foreach { el =>
            val title = el >> text
            val hash = el >> attr( "value" )

            if ( !hash.isBlank ) {
                try {
                    val decodedIframeString = new String( Base64.getDecoder.decode( hash ), StandardCharsets.UTF_8 )

                    val url = browser.parseString( decodedIframeString ).body >> element( "iframe" ) >> attr( "src" )

                    ret.addOne( AnimePagePlayer( title, url ) )
                } catch {
                    case e: Exception =>
                        e.printStackTrace()

                }

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
