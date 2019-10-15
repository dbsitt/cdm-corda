package com.derivhack

import net.corda.core.utilities.minutes

class Constant {
    companion object Factory {
        val DEFAULT_DURATION = 30.minutes
        val DEFAULT_COLLATERAL_RATE = 1.1
        val COLLATERAL_WALLET_TYPE = "COLLATERAL"
        val TRADING_WALLET_TYPE = "TRADING"
    }
}