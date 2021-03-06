import org.scalatest.FunSuite

class AnimeZoneTest extends FunSuite {

    test( "AnimeZoneTest GetAnimeList" ) {
        val service = new AnimeZonePL()

        val animeList = service.getAnimeList

/*        animeList.foreach { it =>
            println( s"${it.title} -> ${it.url}" )
        }

        assert( animeList.length > 0 )*/
    }

}
