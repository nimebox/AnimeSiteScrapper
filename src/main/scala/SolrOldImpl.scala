import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

import Models.{AnimeEpisodePlayerSorl, AnimeEpisodeSorl, AnimeSorl}
import io.ino.solrs.AsyncSolrClient
import io.ino.solrs.future.ScalaFutureFactory.Implicit
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.SolrInputDocument
import org.joda.time.DateTime
import scalaz.StreamT.Done

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}

class SolrOldImpl( val serviceName: String ) {
    implicit val context: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService( Executors.newSingleThreadExecutor() )

    object EntryType extends Enumeration
    {
       // type EntryType = Value
        val ANIME, EPISODE, PLAYER = Value
    }

    private val solrService = AsyncSolrClient( s"http://localhost:8984/solr/ao" )
    private val solrEpisodes = AsyncSolrClient( s"http://localhost:8984/solr/aoEps" )
    private val solrPlayers = AsyncSolrClient( s"http://localhost:8984/solr/aoPlayers" )

    /*    def fix( ): Unit = {
            val queryResultFuture = for {
                list <- solr.query( new SolrQuery( "*:*" ).setRows( 3000 ) )
            } yield list

            val queryResult = Await.result( queryResultFuture, Duration.Inf )

            queryResult.getResults.forEach { el =>
                Utils.progressPercentage( queryResult.getResults.indexOf( el ), queryResult.getResults.size() )
                val data = el.getFieldValues( "data" ).toArray
                if ( data.nonEmpty ) {
                    val obj = gson.fromJson( data.head.toString, classOf[ AnimeSorl ] )

                    val animeId = UUID.randomUUID()

                    val doc = new SolrInputDocument()
                    doc.addField( "id", animeId.toString )
                    doc.addField( "title", obj.title )
                    doc.addField( "sourceUrl", obj.url )

                    val epsDocList = ArrayBuffer[ SolrInputDocument ]()
                    val plsDocList = ArrayBuffer[ SolrInputDocument ]()

                    if ( obj.eps.nonEmpty ) {
                        obj.eps.foreach { ep =>
                            val epsd = ep.toSolrDocument( animeId )
                            epsDocList.addOne( epsd._1 )
                            plsDocList.addAll( ep.players.map( _.toSolrDocument( epsd._2 ) ) )
                        }
                    }

                    val dbAdd = for {
                        _ <- solrService.addDocs( docs = Iterable( doc ) )
                        _ <- solrService.commit()

                        _ <- {
                            if ( epsDocList.nonEmpty ) {
                                solrEpisodes.addDocs( docs = epsDocList )
                                solrEpisodes.commit()
                            } else {
                                Future {
                                    Done
                                }
                            }
                        }

                        _ <- {
                            if ( plsDocList.nonEmpty ) {
                                solrPlayers.addDocs( docs = plsDocList )
                                solrPlayers.commit()
                            } else {
                                Future {
                                    Done
                                }
                            }
                        }

                    } yield print("")/*print( s"${obj.title} fixed\n" )*/

                    Await.result( dbAdd, Duration.Inf )

    /*                val get = getDataById2( animeId )

                    println( get )*/
                }
            }
        }*/

    /*def getDataById2( id: UUID ): Option[ AnimeSorl ] = {
        val idString = id.toString
        val existFuture = for {
            anime <- solrService.getById( None, idString )
            episodes <- solrEpisodes.query( new SolrQuery( s"animeId:*$idString" ).setRows( 3000 ) )
        } yield (anime, episodes)

        val exist = Await.result( existFuture, Duration.Inf )

        if ( exist._1.isEmpty ) {
            None
        } else {
            val title = exist._1.get.getFieldValue( "title" ).toString
            val sourceUrl = exist._1.get.getFieldValue( "sourceUrl" ).toString

            if ( exist._2.getResults != null ) {
                val eps = ArrayBuffer[ AnimeEpisodeSorl ]()
                exist._2.getResults.forEach { ep =>
                    val title = ep.getFieldValue( "title" ).toString
                    val url = ep.getFieldValue( "url" ).toString
                    eps.addOne( AnimeEpisodeSorl( title, url, Array[ AnimeEpisodePlayerSorl ]() ) )
                }
                Some( AnimeSorl( title, sourceUrl, eps.toArray ) )
            } else {
                Some( AnimeSorl( title, sourceUrl, Array[ AnimeEpisodeSorl ]() ) )
            }
        }
    }
*/
    /*    def getAllFromDB: List[ AnimeSorl ] = {
            val queryResultFuture = for {
                list <- solr.query( new SolrQuery( "*:*" ).setRows( 2300 ) )
            } yield list

            val queryResult = Await.result( queryResultFuture, Duration.Inf )
            val ret = ArrayBuffer[ AnimeSorl ]()

            queryResult.getResults.forEach { el =>
                val data = el.getFieldValues( "data" ).toArray.head
                ret.addOne( gson.fromJson( data.toString, classOf[ AnimeSorl ] ) )
            }
            ret.toList
        }*/

    def getDataByUrl( url: String ): Option[ AnimeSorl ] = {
        val existAnimeFuture = for {
            anime <- solrService.query( new SolrQuery( s"sourceUrl:*$url" ) )
        } yield anime

        val animeExist = Await.result( existAnimeFuture, Duration.Inf )

        if ( animeExist.getResults.isEmpty ) {
            None
        } else {
            val animeFirstResult = animeExist.getResults.get( 0 )

            val id = UUID.fromString( animeFirstResult.getFieldValue( "id" ).toString )
            val title = animeFirstResult.getFieldValue( "title" ).toString
            val sourceUrl = animeFirstResult.getFieldValue( "sourceUrl" ).toString
            val imageUrl = if ( animeFirstResult.getFieldValue( "imageUrl" ) == null ) None else Some( animeFirstResult.getFieldValue( "imageUrl" ).toString )

            val existFuture = for {
                episodes <- solrEpisodes.query( new SolrQuery( s"animeId:*${animeFirstResult.getFieldValue( "id" ).toString}" ).setRows( 3000 ) )
            } yield episodes

            val episodes = Await.result( existFuture, Duration.Inf )
            if ( episodes.getResults != null ) {
                val eps = ArrayBuffer[ AnimeEpisodeSorl ]()
                episodes.getResults.forEach { ep =>
                    val title = ep.getFieldValue( "title" ).toString
                    val url = ep.getFieldValue( "url" ).toString
                    eps.addOne( AnimeEpisodeSorl( title, url, Array[ AnimeEpisodePlayerSorl ]() ) )
                }
                Some( AnimeSorl( id, title, sourceUrl, imageUrl, eps.toArray ) )
            } else {
                Some( AnimeSorl( id, title, sourceUrl, imageUrl, Array[ AnimeEpisodeSorl ]() ) )
            }
        }
    }

    def getDataByTitle( title: String ): Option[ AnimeSorl ] = {
        val existFuture = for {
            anime <- solrService.query( new SolrQuery( s"title:*$title" ) )
            episodes <- solrEpisodes.query( new SolrQuery( s"animeId:*${anime.getResults.get( 0 ).getFieldValue( "id" ).toString}" ).setRows( 3000 ) )
        } yield (anime, episodes)

        val exist = Await.result( existFuture, Duration.Inf )

        if ( exist._1.getResults.get( 0 ).isEmpty ) {
            None
        } else {
            val id = UUID.fromString( exist._1.getResults.get( 0 ).getFieldValue( "id" ).toString )
            val title = exist._1.getResults.get( 0 ).getFieldValue( "title" ).toString
            val sourceUrl = exist._1.getResults.get( 0 ).getFieldValue( "sourceUrl" ).toString
            val imageUrl = if ( exist._1.getResults.get( 0 ).getFieldValue( "imageUrl" ) == null ) None else Some( exist._1.getResults.get( 0 ).getFieldValue( "imageUrl" ).toString )

            if ( exist._2.getResults != null ) {
                val eps = ArrayBuffer[ AnimeEpisodeSorl ]()
                exist._2.getResults.forEach { ep =>
                    val title = ep.getFieldValue( "title" ).toString
                    val url = ep.getFieldValue( "url" ).toString
                    eps.addOne( AnimeEpisodeSorl( title, url, Array[ AnimeEpisodePlayerSorl ]() ) )
                }
                Some( AnimeSorl( id, title, sourceUrl, imageUrl, eps.toArray ) )
            } else {
                Some( AnimeSorl( id, title, sourceUrl, imageUrl, Array[ AnimeEpisodeSorl ]() ) )
            }
        }
    }

    /*    def getDataById( id: String ): Option[ AnimeSorl ] = {
            val existFuture = for {
                exist <- solr.getById( None, id )
            } yield exist

            val exist = Await.result( existFuture, Duration.Inf )

            if ( exist.isEmpty ) {
                None
            } else {
                val array = exist.get.getFieldValues( "data" ).toArray
                if ( array.isEmpty ) {
                    None
                } else {
                    Some( gson.fromJson( array.head.toString, classOf[ AnimeSorl ] ) )
                }
            }
        }*/

    def save( id: UUID, title: String, as: AnimeSorl, onlyAp: Boolean = false ): Unit = {
        val doc = new SolrInputDocument()
        doc.addField( "id", id.toString )
        doc.addField( "title", as.title )
        if ( as.imageUrl.isDefined ) {
            doc.addField( "imageUrl", as.imageUrl.get )
        }
        doc.addField( "sourceUrl", as.url )
        doc.addField("updated", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT))

        val epsDocList = ArrayBuffer[ SolrInputDocument ]()
        val plsDocList = ArrayBuffer[ SolrInputDocument ]()

        if(!onlyAp) {
            if ( as.eps.nonEmpty ) {
                as.eps.foreach { ep =>
                    val epsd = ep.toSolrDocument( id )
                    epsDocList.addOne( epsd._1 )
                    plsDocList.addAll( ep.players.map( _.toSolrDocument( epsd._2 ) ) )
                }
            }
        }

        val dbAdd = for {
            _ <- solrService.addDocs( docs = Iterable( doc ) )
            _ <- solrService.commit()

            _ <- {
                if(onlyAp) {
                    Future {
                        Done
                    }
                } else {
                    if ( epsDocList.nonEmpty ) {
                        solrEpisodes.addDocs( docs = epsDocList )
                        solrEpisodes.commit()
                    } else {
                        Future {
                            Done
                        }
                    }
                }
            }

            _ <- {
                if(onlyAp) {
                    Future {
                        Done
                    }
                } else {
                    if ( plsDocList.nonEmpty ) {
                        solrPlayers.addDocs( docs = plsDocList )
                        solrPlayers.commit()
                    } else {
                        Future {
                            Done
                        }
                    }
                }
            }

        } yield print( s"$title added\n" )

        Await.result( dbAdd, Duration.Inf )
    }
}
