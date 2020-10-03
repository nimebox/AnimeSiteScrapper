import java.util.concurrent.Executors

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}

object Main {
    def main( args: Array[ String ] ): Unit = {
        implicit val context: ExecutionContextExecutorService =
            ExecutionContext.fromExecutorService( Executors.newFixedThreadPool( 6 ) )

        val r1 = Future {
            val service = new AnimeOdcinkiPl()
            service.updateAnimeDB()
        }

        val r2 = Future {
            val service = new AnimeDesuPL()
            service.updateAnimeDB()
        }

        Await.ready(Future.sequence(List(r1, r2)), Duration.Inf)
    }
}
