package com.wellingtonmb88.bitwallet

import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.wallet.Wallet
import java.util.*

class BitcoinUIModel {

    private lateinit var address: Address
    private var balance = Coin.ZERO
    private var syncProgress: Double = -1.0
    private var syncProgressUpdater = ProgressBarUpdater()

    constructor(wallet: Wallet) {
        setWallet(wallet)
    }

    private fun setWallet(wallet: Wallet) {
        wallet.addChangeEventListener { wallet ->
            update(wallet)
        }
        update(wallet)
    }

    private fun update(wallet: Wallet) {
        balance = wallet.balance
        address = wallet.currentReceiveAddress()
    }

    private inner class ProgressBarUpdater : DownloadProgressTracker() {
        override fun progress(pct: Double, blocksLeft: Int, date: Date) {
            super.progress(pct, blocksLeft, date)
            syncProgress = pct / 100.0
        }

        override fun doneDownload() {
            super.doneDownload()
            syncProgress = 1.0
        }
    }


    fun getDownloadProgressTracker(): DownloadProgressTracker {
        return syncProgressUpdater
    }

    fun syncProgressProperty(): Double {
        return syncProgress
    }

    fun addressProperty(): Address {
        return address
    }

    fun balanceProperty(): Coin {
        return balance
    }
}