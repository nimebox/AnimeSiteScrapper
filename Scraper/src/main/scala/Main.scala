import java.util.concurrent.Executors

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}

object Main {
    def main( args: Array[ String ] ): Unit = {
        implicit val context: ExecutionContextExecutorService =
            ExecutionContext.fromExecutorService( Executors.newFixedThreadPool( 6 ) )

        val r3 = Future {
            val service = new AnimeZonePL()
            service.updateAnimeDB()
        }


        Await.ready(Future.sequence(List(r3)), Duration.Inf)
    }
}
