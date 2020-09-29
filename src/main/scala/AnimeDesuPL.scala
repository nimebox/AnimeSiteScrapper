import java.util.concurrent.Executors

import com.google.gson.GsonBuilder
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import org.apache.commons.codec.binary.Base64
import sttp.client._
import org.jsoup.{Connection, HttpStatusException}
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class AnimeDesuPL( ) {
    case class AnimePage( title: String, url: String, imageB64: Option[ String ] = None )

    case class AnimeEpisode( title: String, url: String )

    implicit val context: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService( Executors.newSingleThreadExecutor() )

    private val browser = new JsoupBrowser( userAgent = Const.ua ) {
        override def requestSettings( conn: Connection ): Connection = conn.timeout( 60000 )
    }

    implicit val backend: SttpBackend[ Identity, Nothing, NothingT ] = HttpURLConnectionBackend()


    browser.setCookie( "animedesu.pl", "age_gate", "18" )

    private val gson = new GsonBuilder().create()

    def getAnimeList: Array[ AnimePage ] = {
        val ret = ArrayBuffer[ AnimePage ]()
        println( "Getting data" )

        val animeList = browser.get( "https://animedesu.pl/anime/list-mode/" )

        ret.addAll( ( animeList >> elementList( ".soralist .blix ul li a" ) ).map { el =>
            AnimePage( el >> text, el >> attr( "href" ) )
        } )
        ret.toArray
    }

    def getAnimeEpisodes( animePage: AnimePage ): (Array[ AnimeEpisode ], Option[ String ]) = {
        val ret = ArrayBuffer[ AnimeEpisode ]()
        println( s"Getting episodes for ${animePage.title}" )

        val animeList = browser.get( animePage.url )

        ret.addAll( ( animeList >> elementList( ".epcheck .eplister ul li a" ) ).map { el =>
            AnimeEpisode( el >> element( ".epl-title" ) >> text, el >> attr( "href" ) )
        } )

        val imageUrl = animeList >> element( ".thumbook .thumb img" ) >> attr( "src" )
        val image = downloadImage( imageUrl, animePage.url )

        (ret.toArray, image)
    }

    def downloadImage( url: String, refererUrl: String ): Option[ String ] = {
        val request = basicRequest
            .header( "User-Agent", Const.ua )
            .header( "Referer", refererUrl )
            .get( uri"$url" )
            .response( asByteArray )

        val response = request.send()

        if ( response.is200 ) {
            response.body.fold( l => None, r => Some( Base64.encodeBase64String( r ) ) )
        } else {
            None
        }
    }
}
