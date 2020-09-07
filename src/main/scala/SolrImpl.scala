import java.util.concurrent.Executors

import Models.AnimeSorl
import com.google.gson.GsonBuilder
import io.ino.solrs.AsyncSolrClient
import io.ino.solrs.future.ScalaFutureFactory.Implicit
import org.apache.solr.common.SolrInputDocument

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService}


class SolrImpl( val serviceName: String ) {
    implicit val context: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService( Executors.newSingleThreadExecutor() )

    private val gson = new GsonBuilder().create()
    private val solr = AsyncSolrClient( s"http://localhost:8984/solr/$serviceName" )

    def getDataById( id: String ): Option[ AnimeSorl ] = {
        val existFuture = for {
            exist <- solr.getById( None, id )
        } yield exist

        val exist = Await.result( existFuture, Duration.Inf )
        val array = exist.fold( None.asInstanceOf[ Array[ AnyRef ] ] )( obj => obj.getFieldValues( "data" ).toArray )

        if ( array.isEmpty ) {
            None
        } else {
            Some( gson.fromJson( array.head.toString, classOf[ AnimeSorl ] ) )
        }
    }

    def save( id: String, title: String, as: AnimeSorl ): Unit = {
        val doc = new SolrInputDocument()
        doc.addField( "id", id )
        doc.addField( "title", title )
        doc.addField( "data", gson.toJson( as ) )

        val dbAdd = for {
            _ <- solr.addDocs( docs = Iterable( doc ) )
            _ <- solr.commit()
        } yield print( s"$title added\n" )

        Await.result( dbAdd, Duration.Inf )
    }
}
