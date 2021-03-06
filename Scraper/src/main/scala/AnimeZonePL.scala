import java.util.Base64

import Models.AnimePage
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import org.jsoup.Connection
import sttp.client._
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class AnimeZonePL {
    private val browser = new JsoupBrowser( userAgent = Const.ua ) {
        override def requestSettings( conn: Connection ): Connection = conn.timeout( 60000 )
    }

    implicit val backend: SttpBackend[ Identity, Nothing, NothingT ] = HttpURLConnectionBackend()

    private val solrImpl = new SolrImpl( "az" )

    final val domain = "https://www.animezone.pl"

    def updateAnimeDB( ): Unit = {
        getAnimeList.foreach { anime =>

        }
    }

    def getAnimeList: Array[ AnimePage ] = {
        val ret = ArrayBuffer[ AnimePage ]()
        val urlList = ArrayBuffer[ String ]()
        var complete = false

        urlList.addAll( getAnimeLetterUrls )

        while ( !complete ) {
            val url = urlList.remove( 0 )
            Utils.randomTimeout()

            val animeAndNextUrl = getAnimeForPage( url )

            urlList.addAll( animeAndNextUrl._2 )
            ret.addAll( animeAndNextUrl._1 )

            if ( urlList.isEmpty ) {
                complete = true
            }
        }

        ret.toArray
    }

    private def getAnimeLetterUrls: Array[ String ] = {
        val ret = ArrayBuffer[ String ]()

        val tvSeries = browser.get( s"$domain/anime/lista" )

        ret.addAll( ( tvSeries >> elementList( ".anime-list > div > a" ) ).map { el =>
            s"$domain${el >> attr( "href" )}"
        } )

        val movieSeries = browser.get( s"$domain/anime/filmy" )

        ret.addAll( ( movieSeries >> elementList( ".anime-list > div > a" ) ).map { el =>
            s"$domain${el >> attr( "href" )}"
        } )

        ret.sorted.toArray
    }

    private def getAnimeForPage( url: String ): (Array[ AnimePage ], Array[ String ]) = {
        val ret = ArrayBuffer[ AnimePage ]()
        val ret2 = ArrayBuffer[ String ]()

        val page = browser.get( url )

        ret.addAll( ( page >> elementList( ".categories-newest > .categories" ) ).map { el =>

            val imgHalfUrl = el >> element( ".image > a > img" ) >> attr( "src" )
            val image = downloadImage( s"$domain$imgHalfUrl", url )

            val title = el >> element( ".description > .label > a" ) >> text
            val animeHalfUrl = el >> element( ".description > .label > a" ) >> attr( "href" )
            val animeUrl = s"$domain$animeHalfUrl"

            AnimePage( title, animeUrl, image )
        } )

        val paginationList = page >> elementList( ".pagination > li" )

        if ( paginationList.isEmpty || ( paginationList( 1 ) >> attr( "class" ) != "active" ) ) {
            return (ret.toArray, ret2.toArray)
        }

        try {
            val maxPages = ( paginationList( paginationList.size - 2 ) >> text ).toInt

            for ( r <- 2 to maxPages ) {
                ret2.addOne( s"$url?page=$r" )
            }
        } catch {
            case e: Exception =>
                e.printStackTrace()
        }

        (ret.toArray, ret2.toArray)
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
