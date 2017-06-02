package com.wellingtonmb88.bitwallet

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.DeterministicSeed
import java.io.File


class MainActivity : AppCompatActivity() {

    var APP_NAME = "BitWallet"

    lateinit var params: NetworkParameters
    var bitcoin: WalletAppKit? = null

    private lateinit var model: BitcoinUIModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        params = TestNet3Params()
    }

    override fun onStop() {
        super.onStop()
        bitcoin?.stopAsync()
        bitcoin?.awaitTerminated()
    }

    override fun onResume() {
        super.onResume()
        setupWalletKit(null)
        bitcoin?.startAsync()
    }

    fun setupWalletKit(seed: DeterministicSeed?) {

        // If seed is non-null it means we are restoring from backup.
        bitcoin = object : WalletAppKit(params, File("."), APP_NAME + "-" + params.paymentProtocolId) {
            override fun onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                bitcoin?.wallet()?.allowSpendingUnconfirmedTransactions()
                onBitcoinSetup()
            }
        }

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            bitcoin?.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
        } else if (params == TestNet3Params.get()) {
            // As an example!
            bitcoin?.useTor()
            // bitcoin.setDiscovery(new HttpDiscovery(params, URI.create("http://localhost:8080/peers"), ECKey.fromPublicOnly(BaseEncoding.base16().decode("02cba68cfd0679d10b186288b75a59f9132b1b3e222f6332717cb8c4eb2040f940".toUpperCase()))));
        }

        bitcoin?.setDownloadListener(model.getDownloadProgressTracker())
                ?.setBlockingStartup(false)
                ?.setUserAgent(APP_NAME, "1.0")

        if (seed != null) {
            bitcoin?.restoreWalletFromSeed(seed)
        }
    }

    fun onBitcoinSetup() {
        bitcoin?.let {
            model = BitcoinUIModel(it.wallet())
            print("addressProperty = ${model.addressProperty()}")
            print("balanceProperty = ${model.balanceProperty()}")
            print("syncProgressProperty = ${model.syncProgressProperty()}")
        }
    }
}
