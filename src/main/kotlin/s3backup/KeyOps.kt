package s3backup

import java.io.File
import java.io.IOException
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object KeyOps {

    fun generateKeyPair(algorithm: String, keySize: Int): KeyPair {
        val keyGenerator = KeyPairGenerator.getInstance(algorithm)
        keyGenerator.initialize(keySize, SecureRandom())
        return keyGenerator.generateKeyPair()
    }

    @Throws(IOException::class)
    fun saveKeyPair(publicKeyFile: File, privateKeyFile: File, keyPair: KeyPair) {
        publicKeyFile.writeBytes(X509EncodedKeySpec(keyPair.public.encoded).encoded)
        println("Public key saved to $publicKeyFile")
        privateKeyFile.writeBytes(PKCS8EncodedKeySpec(keyPair.private.encoded).encoded)
        println("Private key saved to $privateKeyFile")
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun loadKeyPair(publicKeyFile: File, privateKeyFile: File, algorithm: String): KeyPair {
        val keyFactory = KeyFactory.getInstance(algorithm)
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyFile.readBytes()))
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyFile.readBytes()))
        return KeyPair(publicKey, privateKey)
    }
}