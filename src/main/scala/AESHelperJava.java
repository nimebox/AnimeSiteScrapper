import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESHelperJava {
    /**
     * Conforming with CryptoJS AES method
     * **** YOU NEED TO ADD "JCE policy" to have ability to DEC/ENC 256 key-lenght with AES Cipher ****
     */

    // Prevent facing error when using "PKCS7Padding" in Cipher
    // add Bouncycastle provider [link=http://www.bouncycastle.org/latest_releases.html]
    static {
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }


    static int KEY_SIZE = 256;
    static int IV_SIZE = 128;
    static String HASH_CIPHER = "AES/CBC/PKCS7Padding";
    static String AES = "AES";
    static String CHARSET_TYPE = "UTF-8";
    static String KDF_DIGEST = "MD5";


    // Seriously crypto-js, what's wrong with you?
    static String APPEND = "Salted__";

    /**
     * Encrypt
     *
     * @param password  passphrase
     * @param plainText plain string
     */
    public static String encrypt( String password, String plainText ) throws UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        byte[] saltBytes = generateSalt( 8 );
        byte[] key = new byte[KEY_SIZE / 8];
        byte[] iv = new byte[IV_SIZE / 8];

        EvpKDF( password.getBytes( CHARSET_TYPE ), KEY_SIZE, IV_SIZE, saltBytes, key, iv );

        SecretKey keyS = new SecretKeySpec( key, AES );

        Cipher cipher = Cipher.getInstance( HASH_CIPHER );
        IvParameterSpec ivSpec = new IvParameterSpec( iv );
        cipher.init( Cipher.ENCRYPT_MODE, keyS, ivSpec );
        byte[] cipherText = cipher.doFinal( plainText.getBytes( CHARSET_TYPE ) );

        // Thanks kientux for this: https://gist.github.com/kientux/bb48259c6f2133e628ad
        // Create CryptoJS-like encrypted !

        byte[] sBytes = APPEND.getBytes( CHARSET_TYPE );
        byte[] b = new byte[sBytes.length + saltBytes.length + cipherText.length];
        System.arraycopy( sBytes, 0, b, 0, sBytes.length );
        System.arraycopy( saltBytes, 0, b, sBytes.length, saltBytes.length );
        System.arraycopy( cipherText, 0, b, sBytes.length + saltBytes.length, cipherText.length );

        byte[] bEncode = new Base64().encode( b );

        return new String( bEncode );
    }

    /**
     * Decrypt
     * Thanks Artjom B. for this: http://stackoverflow.com/a/29152379/4405051
     *
     * @param password   passphrase
     * @param cipherText encrypted string
     */
    public static String decrypt( String password, byte[] salt, String cipherText ) throws UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        byte[] ctBytes = new Base64().decode( cipherText.getBytes( CHARSET_TYPE ) );
        byte[] key = new byte[KEY_SIZE / 8];
        byte[] iv = new byte[IV_SIZE / 8];

        EvpKDF( password.getBytes( CHARSET_TYPE ), KEY_SIZE, IV_SIZE, salt, key, iv );

        Cipher cipher = Cipher.getInstance( HASH_CIPHER );
        SecretKey keyS = new SecretKeySpec( key, AES );

        cipher.init( Cipher.DECRYPT_MODE, keyS, new IvParameterSpec( iv ) );
        byte[] plainText = cipher.doFinal( ctBytes );
        return new String( plainText );
    }

    private static byte[] EvpKDF( byte[] password, int keySize, int ivSize, byte[] salt, byte[] resultKey, byte[] resultIv ) throws NoSuchAlgorithmException {
        return EvpKDF( password, keySize, ivSize, salt, 1, KDF_DIGEST, resultKey, resultIv );
    }

    private static byte[] EvpKDF( byte[] password, int keySize, int ivSize, byte[] salt, int iterations, String hashAlgorithm, byte[] resultKey, byte[] resultIv ) throws NoSuchAlgorithmException {
        keySize = keySize / 32;
        ivSize = ivSize / 32;
        int targetKeySize = keySize + ivSize;
        byte[] derivedBytes = new byte[targetKeySize * 4];
        int numberOfDerivedWords = 0;
        byte[] block = null;
        MessageDigest hasher = MessageDigest.getInstance( hashAlgorithm );
        while ( numberOfDerivedWords < targetKeySize ) {
            if ( block != null ) {
                hasher.update( block );
            }
            hasher.update( password );
            block = hasher.digest( salt );
            hasher.reset();

            // Iterations
            for ( int i = 1; i < iterations; i++ ) {
                block = hasher.digest( block );
                hasher.reset();
            }

            System.arraycopy( block, 0, derivedBytes, numberOfDerivedWords * 4,
                    Math.min( block.length, ( targetKeySize - numberOfDerivedWords ) * 4 ) );

            numberOfDerivedWords += block.length / 4;
        }

        System.arraycopy( derivedBytes, 0, resultKey, 0, keySize * 4 );
        System.arraycopy( derivedBytes, keySize * 4, resultIv, 0, ivSize * 4 );

        return derivedBytes; // key + iv
    }

    private static byte[] generateSalt( int length ) {
        Random r = new SecureRandom();
        byte[] salt = new byte[length];
        r.nextBytes( salt );
        return salt;
    }
}
