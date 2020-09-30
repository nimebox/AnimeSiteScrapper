import java.security.{MessageDigest, SecureRandom}
import org.apache.commons.codec.binary.Base64

import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

object AESHelper {
    val KEY_SIZE = 256
    val IV_SIZE = 128
    val HASH_CIPHER = "AES/CBC/PKCS5Padding"
    val AES = "AES"
    val CHARSET_TYPE = "UTF-8"
    val KDF_DIGEST = "MD5"

    // Seriously crypto-js, what's wrong with you?
    val APPEND = "Salted__"

    def encrypt( password: String, plainText: String ): String = {
        val saltBytes = generateSalt( 8 )
        val key = new Array[ Byte ]( KEY_SIZE / 8 )
        val iv = new Array[ Byte ]( IV_SIZE / 8 )

        EvpKDF( password.getBytes( CHARSET_TYPE ), KEY_SIZE, IV_SIZE, saltBytes, key, iv )

        val keyS = new SecretKeySpec( key, AES )

        val cipher = Cipher.getInstance( HASH_CIPHER )
        val ivSpec = new IvParameterSpec( iv )
        cipher.init( Cipher.ENCRYPT_MODE, keyS, ivSpec )
        val cipherText = cipher.doFinal( plainText.getBytes( CHARSET_TYPE ) )

        val sBytes = APPEND.getBytes( CHARSET_TYPE )
        val b = new Array[ Byte ]( sBytes.length + saltBytes.length + cipherText.length )
        System.arraycopy( sBytes, 0, b, 0, sBytes.length )
        System.arraycopy( saltBytes, 0, b, sBytes.length, saltBytes.length )
        System.arraycopy( cipherText, 0, b, sBytes.length + saltBytes.length, cipherText.length )
        val bEncode = new Base64().encode( b )

        new String( bEncode )
    }

    def decrypt( password: String, salt: Array[ Byte ], cipherText: String ): String = {
        val ctBytes = new Base64().decode( cipherText.getBytes( CHARSET_TYPE ) )
        val key = new Array[ Byte ]( KEY_SIZE / 8 )
        val iv = new Array[ Byte ]( IV_SIZE / 8 )

        EvpKDF( password.getBytes( CHARSET_TYPE ), KEY_SIZE, IV_SIZE, salt, key, iv )

        val cipher = Cipher.getInstance( HASH_CIPHER )
        val keyS = new SecretKeySpec( key, AES )

        cipher.init( Cipher.DECRYPT_MODE, keyS, new IvParameterSpec( iv ) )
        val plainText = cipher.doFinal( ctBytes )
        new String( plainText )
    }

    private def EvpKDF( password: Array[ Byte ], keySize: Int, ivSize: Int, salt: Array[ Byte ], resultKey: Array[ Byte ], resultIv: Array[ Byte ] ): Array[ Byte ] = {
        EvpKDF( password, keySize, ivSize, salt, 1, KDF_DIGEST, resultKey, resultIv )
    }

    private def EvpKDF( password: Array[ Byte ], keySize: Int, ivSize: Int, salt: Array[ Byte ], iterations: Int, hashAlgorithm: String, resultKey: Array[ Byte ], resultIv: Array[ Byte ] ): Array[ Byte ] = {
        val keySizeNew = keySize / 32
        val ivSizeNew = ivSize / 32
        val targetKeySize = keySizeNew + ivSizeNew
        val derivedBytes = new Array[ Byte ]( targetKeySize * 4 )
        var numberOfDerivedWords = 0
        var block: Array[ Byte ] = null
        val hasher = MessageDigest.getInstance( hashAlgorithm )

        while ( {
            numberOfDerivedWords < targetKeySize
        } ) {
            if ( block != null ) hasher.update( block )
            hasher.update( password )
            block = hasher.digest( salt )
            hasher.reset()
            // Iterations
            for ( _ <- 1 until iterations ) {
                block = hasher.digest( block )
                hasher.reset()
            }
            System.arraycopy( block, 0, derivedBytes, numberOfDerivedWords * 4, Math.min( block.length, ( targetKeySize - numberOfDerivedWords ) * 4 ) )
            numberOfDerivedWords += block.length / 4
        }

        System.arraycopy( derivedBytes, 0, resultKey, 0, keySizeNew * 4 )
        System.arraycopy( derivedBytes, keySizeNew * 4, resultIv, 0, ivSizeNew * 4 )

        derivedBytes // key + iv
    }

    private def generateSalt( length: Int ): Array[ Byte ] = {
        val r = new SecureRandom()
        val salt = new Array[ Byte ]( length )
        r.nextBytes( salt )
        salt
    }

}
