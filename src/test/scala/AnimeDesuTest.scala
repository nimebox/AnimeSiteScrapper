import org.scalatest.FunSuite

class AnimeDesuTest extends FunSuite {

    test( "GetAnimeList" ) {
        val service = new AnimeDesuPL()

        val animeList = service.getAnimeList

        animeList.foreach{ it =>
            println(s"${it.title} -> ${it.url}")
        }

        assert(animeList.length > 0)
    }

    test( "GetEpisodeList" ) {
        val service = new AnimeDesuPL()

        val testPage = service.AnimePage( "B-gata H-kei", "https://animedesu.pl/anime/b-gata-h-kei/" )

        val episodeList = service.getAnimeEpisodes(testPage)._1

        episodeList.foreach{ it =>
            println(s"${it.title} -> ${it.url}")
        }

        assert(episodeList.length == 12)
    }

}
