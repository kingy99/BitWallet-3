package com.wellingtonmb88.bitwallet

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.subgraph.orchid.TorInitializationListener
import org.bitcoinj.core.Coin
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.utils.BriefLogFormatter
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.listeners.AbstractWalletEventListener
import java.util.*
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    var APP_NAME = "BitWallet"

    lateinit var params: NetworkParameters
    var bitcoin: WalletAppKit? = null

    private lateinit var model: BitcoinUIModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        params = MainNetParams.get()
        params = TestNet3Params.get()
    }

    override fun onStop() {
        super.onStop()
        bitcoin?.stopAsync()
        bitcoin?.awaitTerminated()
    }

    override fun onResume() {
        super.onResume()
        val executor = Executors.newSingleThreadExecutor()

//        executor.execute {
            // Make log output concise.
            BriefLogFormatter.init()

            setupWalletKit(null)

//            WalletSetPasswordController().estimateKeyDerivationTimeMsec();

            bitcoin?.startAsync()

//            bitcoin?.awaitRunning()
//            bitcoin?.peerGroup()?.downloadPeer?.close()
//        }
    }

    fun setupWalletKit(seed: DeterministicSeed?) {

        // If seed is non-null it means we are restoring from backup.

        bitcoin = object : WalletAppKit(params, filesDir, APP_NAME + "-" + params.paymentProtocolId) {
            override fun onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                bitcoin?.wallet()?.allowSpendingUnconfirmedTransactions()

//                if (bitcoin?.wallet()?.keyChainGroupSize!! < 1) {
//                    bitcoin?.wallet()?.importKey(ECKey())
//                }
                bitcoin?.peerGroup()?.fastCatchupTimeSecs =  0 //bitcoin?.wallet().earliestKeyCreationTime


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
//            bitcoin?.setDiscovery( HttpDiscovery(params, URI.create("http://localhost:8080/peers"), ECKey.fromPublicOnly(BaseEncoding.base16().decode("02cba68cfd0679d10b186288b75a59f9132b1b3e222f6332717cb8c4eb2040f940".toUpperCase()))));
        }


        model = BitcoinUIModel()

        bitcoin?.setDownloadListener(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksLeft: Int, date: Date) {
                super.progress(pct, blocksLeft, date)
                val syncProgress = pct / 100.0
                Log.d(APP_NAME, "Download progress = $syncProgress")
            }

            override fun doneDownload() {
                super.doneDownload()

                Log.d(APP_NAME, "doneDownload")
            }
        })?.setBlockingStartup(false)
                ?.setUserAgent(APP_NAME, "1.0")


        if (seed != null) {
            bitcoin?.restoreWalletFromSeed(seed)
        }
    }

    fun onBitcoinSetup() {
        bitcoin?.let {
            // wallet = mwrT7sgyE2uy197wTbVD3nSzLtPCDhL1Yq
            model.setWallet(it.wallet())
            print("addressProperty = ${model.addressProperty()}")
            print("balanceProperty = ${model.balanceProperty()}")
            print("syncProgressProperty = ${model.syncProgressProperty()}")

            val torClient = it.peerGroup().getTorClient()


            if (torClient != null) {
                val torMsg = "Initialising Tor";
                torClient.addInitializationListener(object : TorInitializationListener {
                    override fun initializationProgress(message: String, percent: Int) {

                        Log.d(APP_NAME, "initializationProgress = message: $message , percent = ${percent / 100.0}")
                    }

                    override fun initializationCompleted() {
                        Log.d(APP_NAME, "initializationCompleted")
                    }
                })

            } else {
                Log.d(APP_NAME, "showBitcoinSyncMessage")
            }


            val chain = bitcoin?.chain()
            val bs = chain?.getBlockStore()
            val peer = bitcoin?.peerGroup()?.getDownloadPeer()
            val b = peer?.getBlock(bs?.getChainHead()?.getHeader()?.getHash())?.get()
            Log.d(APP_NAME, "getBlock = $b ")



            it.wallet().addEventListener(object : AbstractWalletEventListener() {
                override fun onCoinsReceived(w: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin) {
                    // Runs in the dedicated "user thread".
                    Log.d(APP_NAME, "onCoinsReceived")
                }

                override fun onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction){
                    Log.d(APP_NAME, "onTransactionConfidenceChanged")
                }
            })

        }


    }
}
