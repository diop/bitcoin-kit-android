package io.horizontalsystems.bitcoinkit.demo

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import io.horizontalsystems.bitcoinkit.BitcoinCore

class BalanceFragment : Fragment() {

    lateinit var viewModel: MainViewModel
    lateinit var networkName: TextView
    lateinit var balanceValue: TextView
    lateinit var lastBlockValue: TextView
    lateinit var stateValue: TextView
    lateinit var startButton: Button
    lateinit var clearButton: Button
    lateinit var buttonDebug: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)

            viewModel.balance.observe(this, Observer { balance ->
                balanceValue.text = when(balance) {
                    null -> ""
                    else -> NumberFormatHelper.cryptoAmountFormat.format(balance / 100_000_000.0)
                }
            })

            viewModel.lastBlockHeight.observe(this, Observer {
                lastBlockValue.text = (it ?: 0).toString()
            })

            viewModel.state.observe(this, Observer {
                when (it) {
                    is BitcoinCore.KitState.Synced -> {
                        stateValue.text = "synced"
                    }
                    is BitcoinCore.KitState.Syncing -> {
                        stateValue.text = "syncing ${it.progress}"
                    }
                    is BitcoinCore.KitState.NotSynced -> {
                        stateValue.text = "not synced"
                    }
                }
            })

            viewModel.status.observe(this, Observer {
                when (it) {
                    MainViewModel.State.STARTED -> {
                        startButton.isEnabled = false
                    }
                    else -> {
                        startButton.isEnabled = true
                    }
                }
            })

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_balance, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        networkName = view.findViewById(R.id.networkName)
        networkName.text = viewModel.networkName

        balanceValue = view.findViewById(R.id.balanceValue)
        lastBlockValue = view.findViewById(R.id.lastBlockValue)
        stateValue = view.findViewById(R.id.stateValue)
        startButton = view.findViewById(R.id.buttonStart)
        clearButton = view.findViewById(R.id.buttonClear)
        buttonDebug = view.findViewById(R.id.buttonDebug)

        startButton.setOnClickListener {
            viewModel.start()
        }

        clearButton.setOnClickListener {
            viewModel.clear()
        }

        buttonDebug.setOnClickListener {
            viewModel.showDebugInfo()
        }
    }
}
