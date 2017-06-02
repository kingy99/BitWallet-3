package com.wellingtonmb88.bitwallet

import com.google.protobuf.ByteString
import org.bitcoinj.crypto.KeyCrypterScrypt
import org.bitcoinj.wallet.Protos
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture


class WalletSetPasswordController {
    private val log = LoggerFactory.getLogger(WalletSetPasswordController::class.java)

    val SCRYPT_PARAMETERS = Protos.ScryptParameters.newBuilder()
            .setP(6)
            .setR(8)
            .setN(32768)
            .setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt()))
            .build()

    var estimatedKeyDerivationTime: Long? = null

    fun estimateKeyDerivationTimeMsec(): CompletableFuture<Long> {
        // This is run in the background after startup. If we haven't recorded it before, do a key derivation to see
        // how long it takes. This helps us produce better progress feedback, as on Windows we don't currently have a
        // native Scrypt impl and the Java version is ~3 times slower, plus it depends a lot on CPU speed.
        val future = CompletableFuture<Long>()
        Thread {
            log.info("Doing background test key derivation")
            val scrypt = KeyCrypterScrypt(SCRYPT_PARAMETERS)
            val start = System.currentTimeMillis()
            scrypt.deriveKey("test password")
            val msec = System.currentTimeMillis() - start
            log.info("Background test key derivation took {}msec", msec)
            estimatedKeyDerivationTime = msec
            future.complete(estimatedKeyDerivationTime)
        }.start()
        return future
    }

}