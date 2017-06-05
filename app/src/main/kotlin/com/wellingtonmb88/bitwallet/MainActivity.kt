package com.wellingtonmb88.bitwallet

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.*
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.utils.BriefLogFormatter
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.listeners.AbstractWalletEventListener
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private val addressTextView: TextView by lazy { this.findViewById(R.id.wallet_address) as TextView }
    private val balanceTextView: TextView by lazy { this.findViewById(R.id.balance) as TextView }
    private val sendBitcoinToAddressEditText: EditText by lazy { this.findViewById(R.id.wallet_address_to_send) as EditText }
    private val sendBitcoinEditText: EditText by lazy { this.findViewById(R.id.sendBitcoinValue) as EditText }
    private val sendBitcoinButton: Button by lazy { this.findViewById(R.id.sendBitcoinButton) as Button }
    private val progressBar: ProgressBar by lazy { this.findViewById(R.id.progessBar) as ProgressBar }

    private var APP_NAME = "BitWallet"

    private lateinit var params: NetworkParameters
    private lateinit var model: BitcoinUIModel
    private var walletAppKit: WalletAppKit? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        params = MainNetParams.get()
//        params = TestNet3Params.get()
    }

    override fun onStop() {
        super.onStop()
        walletAppKit?.stopAsync()
        walletAppKit?.awaitTerminated()
    }

    override fun onResume() {
        super.onResume()
        val executor = Executors.newSingleThreadExecutor()

        executor.execute {
            // Make log output concise.
            BriefLogFormatter.init()

            setupWalletKit(null)

            walletAppKit?.startAsync()
            walletAppKit?.awaitRunning()

            sleep(1000)

            walletAppKit?.wallet()?.addEventListener(object : AbstractWalletEventListener() {
                override fun onCoinsReceived(wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin) {
                    super.onCoinsReceived(wallet, tx, prevBalance, newBalance)
                    // Runs in the dedicated "user thread".
                    updateBalance(newBalance)
                    hideProgressBar()
                    showToast("Coins were received!")
                    Log.d(APP_NAME, "onCoinsReceived")
                }

                override fun onTransactionConfidenceChanged(wallet: Wallet?, tx: Transaction?) {
                    super.onTransactionConfidenceChanged(wallet, tx)
                    Log.d(APP_NAME, "onTransactionConfidenceChanged")
                }

                override fun onWalletChanged(wallet: Wallet?) {
                    super.onWalletChanged(wallet)
                    Log.d(APP_NAME, "onWalletChanged")
                }
            })

            sendBitcoinButton.setOnClickListener {
                showProgressBar()
                val value = sendBitcoinEditText.text.toString()

                if (TextUtils.isEmpty(value)) {
                    hideProgressBar()
                    showToast("Please insert a value!")
                    return@setOnClickListener
                }

                val coin = Coin.parseCoin(value)
                val minTxFee = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE

                val wallet = walletAppKit?.wallet()

                wallet?.let {
                    if (wallet.balance <= Coin.ZERO) {
                        hideProgressBar()
                        showToast("You don`t have enough Bitcoins !")
                        return@setOnClickListener
                    }

                    if (coin < minTxFee) {
                        hideProgressBar()
                        showToast("Value is smaller than the minimum transaction fee: ${MonetaryFormat.BTC.noCode().format(minTxFee)} BTC")
                        return@setOnClickListener
                    }

                    // Adjust how many coins to send. E.g. the minimum; or everything.
                    val sendValue = coin
                    // Coin sendValue = wallet.getBalance().minus(Transaction.DEFAULT_TX_FEE);

                    val TPFAUCET_RETURN_ADR = sendBitcoinToAddressEditText.text.toString()//"mgrnYzNEEM69F7RwJbionCxrGGEZ3WyTzf"

                    if (TextUtils.isEmpty(TPFAUCET_RETURN_ADR)) {
                        hideProgressBar()
                        showToast("Please insert an Address!")
                        return@setOnClickListener
                    }

                    val sendToAdr = Address.fromBase58(params, TPFAUCET_RETURN_ADR)
                    val request = SendRequest.to(sendToAdr, sendValue)

                    val result = it.sendCoins(request)

                    result?.broadcastComplete?.addListener(Runnable {
                        Log.d(APP_NAME, "Coins were sent. Transaction hash: ${result.tx.hashAsString}")
                        hideProgressBar()
                        showToast("Coins were sent!")
                    }, Executors.newSingleThreadExecutor())

                }
            }

        }
    }

    fun setupWalletKit(seed: DeterministicSeed?) {

        // If seed is non-null it means we are restoring from backup.

        walletAppKit = object : WalletAppKit(params, filesDir, APP_NAME + "-" + params.paymentProtocolId) {

            override fun onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                walletAppKit?.wallet()?.allowSpendingUnconfirmedTransactions()

                onBitcoinSetup()
            }
        }

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            walletAppKit?.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
        } else if (params == TestNet3Params.get()) {
            // As an example!
//            walletAppKit?.useTor()
//            walletAppKit?.setDiscovery( HttpDiscovery(params, URI.create("http://localhost:8080/peers"), ECKey.fromPublicOnly(BaseEncoding.base16().decode("02cba68cfd0679d10b186288b75a59f9132b1b3e222f6332717cb8c4eb2040f940".toUpperCase()))));
        }

        model = BitcoinUIModel()

        walletAppKit?.setDownloadListener(object : DownloadProgressTracker() {
            override fun startDownload(blocks: Int) {
                walletAppKit?.peerGroup()?.fastCatchupTimeSecs = 1
                Log.d(APP_NAME, "Downloading block chain of size " + blocks + ". " +
                        if (blocks > 1000) "This may take a while." else "")
            }

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
            walletAppKit?.restoreWalletFromSeed(seed)
        }
    }

    private fun onBitcoinSetup() {
        walletAppKit?.let {
            // wallet = mwrT7sgyE2uy197wTbVD3nSzLtPCDhL1Yq
            val wallet = it.wallet()
            model.setWallet(wallet)

            runOnUiThread {
                Log.d(APP_NAME, "My current Address: ${wallet.currentReceiveAddress()}")
                addressTextView.text = "My Address: ${wallet.currentReceiveAddress()}"
            }

            updateBalance(wallet.balance)
            hideProgressBar()
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(sendBitcoinButton.context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showProgressBar() {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
        }
    }

    private fun hideProgressBar() {
        runOnUiThread {
            progressBar.visibility = View.GONE
        }
    }

    private fun updateBalance(balance: Coin) {
        runOnUiThread {
            balanceTextView.text = "Balance: ${MonetaryFormat.BTC.noCode().format(balance)} BTC"
        }
    }


}
