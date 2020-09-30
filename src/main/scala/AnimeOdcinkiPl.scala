
import java.util.concurrent.Executors

import Models.{AnimeEpisodePlayerSorl, AnimeEpisodeSorl, AnimeSorl}
import Utils.progressPercentage
import com.google.gson.GsonBuilder
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import org.apache.commons.codec.binary.Hex
import org.jsoup.{Connection, HttpStatusException}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}

class AnimeOdcinkiPl( ) {
    implicit val context: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService( Executors.newSingleThreadExecutor() )

    case class DataHash( a: String, b: String, v: String )

    case class AnimePage( title: String, url: String ) {
        def id: String = title.trim.replaceAll( "\\s", "" )
    }

    case class AnimeEpisodePage( title: String, url: String )

    case class AnimeEpisodePlayer( playerName: String, url: String )

    private val key = "s05z9Gpd=syG^7{"

    private val browser = new JsoupBrowser( userAgent = Const.ua ) {
        override def requestSettings( conn: Connection ): Connection = conn.timeout( 60000 )
    }
    private val gson = new GsonBuilder().create()
    private val solrImpl = new SolrImpl( "animeodcinki" )

    def downloadDBAndSave( ): Unit = {
        getList.foreach { ap =>
            println( s"Processing: ${ap.title}" )
            val eps = getEpisodes( ap )

            val exist = solrImpl.getDataById( ap.id )

            if ( exist.isDefined ) {
                if ( exist.get.eps.length != eps.length ) {
                    getPlayersAndSave( ap, eps )
                } else {
                    println( s"${ap.title} exist. Skipping." )
                    Utils.randomTimeout()
                }
            } else {
                getPlayersAndSave( ap, eps )
            }
        }
    }

    private def getPlayersAndSave( ap: AnimePage, episodes: Array[ AnimeEpisodePage ] ): Unit = {
        val players = ArrayBuffer[ (AnimeEpisodePage, Array[ AnimeEpisodePlayer ]) ]()

        episodes.zipWithIndex.foreach { case (ep, idx) =>
            progressPercentage( idx, episodes.length )
            Utils.randomTimeout()
            val epu = getEpisodeUrls( ep )
            if ( !epu.isEmpty ) {
                players.addOne( Tuple2( ep, epu ) )
            }
        }

        if ( episodes.length != 0 ) {
            progressPercentage( episodes.length, episodes.length )
        }

        val item = AnimeSorl( title = ap.title, url = ap.url, eps = players.map { i => AnimeEpisodeSorl( i._1.title, i._1.url, i._2.map { p => AnimeEpisodePlayerSorl( p.playerName, p.url ) } ) }.toArray )

        solrImpl.save( ap.id, ap.title, item )
    }

    def getList: Array[ AnimePage ] = {
        val ret = ArrayBuffer[ AnimePage ]()

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

    def getEpisodes( ap: AnimePage ): Array[ AnimeEpisodePage ] = {
        var ret: Option[ Array[ AnimeEpisodePage ] ] = None

        while ( ret.isEmpty ) {
            try {
                val animePage = browser.get( ap.url )
                ret = Some( ( animePage >> elementList( ".view-lista-odcink-w div ul li" ) ).map { item =>
                    val urlItem = item >> element( "a" )
                    val url = urlItem >> attr( "href" )
                    AnimeEpisodePage( urlItem >> text, url )
                }.toArray )
            } catch {
                case e: Exception =>
                    e.printStackTrace()
                    println( s"Retry getting episodes of ${ap.title}" )
            }
            Utils.randomTimeout()
        }
        ret.get
    }

    def getEpisodeUrls( aep: AnimeEpisodePage ): Array[ AnimeEpisodePlayer ] = {
        var ret: Option[ Array[ AnimeEpisodePlayer ] ] = None
        var canRetry = true

        while ( canRetry ) {
            try {
                val animeEpisodePage = browser.get( aep.url )
                ret = Some( ( animeEpisodePage >> elementList( "#video-player-control div" ) ).map { item =>
                    val playerName = item >> text
                    val dataHash = item >> attr( "data-hash" )
                    val dataHashObj = gson.fromJson( dataHash, classOf[ DataHash ] )
                    val url = AESHelper.decrypt( key, Hex.decodeHex( dataHashObj.v ), dataHashObj.a )
                    AnimeEpisodePlayer( playerName = playerName, url = url.replace( """\/""", "/" ).replace( "\"", "" ) )
                }.toArray )
                canRetry = false
            } catch {
                case e: HttpStatusException =>
                    if ( e.getStatusCode == 404 ) {
                        println( s"Page: ${aep.title} not exist. Skipping." )
                        canRetry = false
                    }
                case e: Exception =>
                    e.printStackTrace()
                    println( s"Retry getting players of ${aep.title}" )
            }
            Utils.randomTimeout()
        }
        ret.fold( Array[ AnimeEpisodePlayer ]() )( e => e )
    }
}
