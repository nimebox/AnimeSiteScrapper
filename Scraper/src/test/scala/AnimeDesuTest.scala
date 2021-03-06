import Models.{AnimePage, AnimePageEpisode}
import org.scalatest.FunSuite

class AnimeDesuTest extends FunSuite {

    test( "GetAnimeList" ) {
        val service = new AnimeDesuPL()

        val animeList = service.getAnimeList

        animeList.foreach { it =>
            println( s"${it.title} -> ${it.url}" )
        }

        assert( animeList.length > 0 )
    }

/*    test( "GetEpisodeList" ) {
        val service = new AnimeDesuPL()

        val testPage = AnimePage( "B-gata H-kei", "https://animedesu.pl/anime/b-gata-h-kei/" )

        val episodeList = service.getAnimeEpisodes( testPage )._1

        episodeList.foreach { it =>
            println( s"${it.title} -> ${it.url}" )
        }

        assert( episodeList.length == 12 )
    }*/

    test( "GetEpisodePlayersList" ) {
        val service = new AnimeDesuPL()

        val testPage = AnimePageEpisode( "B-gata H-kei Odcinek 1", "https://animedesu.pl/b-gata-h-kei-odcinek-1/", Set.empty )

        val playersList = service.getEpisodePlayers( testPage )

        playersList.foreach { it =>
            println( s"${it.title} -> ${it.url}" )
        }

        assert( playersList.toList.length == 4 )
        assert( playersList.head.url == "https://drive.google.com/file/d/1_CEbJZkoae8zzX9mJNcH2uOet9HuzwkF/preview" )
    }

}
