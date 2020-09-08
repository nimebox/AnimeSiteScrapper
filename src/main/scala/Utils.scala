import scala.util.Random

object Utils {
    def progressPercentage( remain: Int, total: Int ): Unit = {
        if ( remain > total ) throw new IllegalArgumentException
        val maxBareSize = 10 // 10unit for 100%
        val remainProcent = ( ( 100 * remain ) / total ) / maxBareSize
        val defaultChar = '-'
        val icon = "="
        val bare = new String( new Array[ Char ]( maxBareSize ) ).replace( '\u0000', defaultChar ) + "]"
        val bareDone = new StringBuilder
        bareDone.append( "[" )
        for ( i <- 0 until remainProcent ) {
            bareDone.append( icon )
        }
        val bareRemain = bare.substring( remainProcent, bare.length )
        print( "\r" + bareDone + bareRemain + " " + remainProcent * maxBareSize + "%  " + remain + "/" + total )
        if ( remain == total ) print( "\n" )
    }

    def randomTimeout(): Unit = {
        Thread.sleep( Random.between(5000, 15000) )
    }
}
