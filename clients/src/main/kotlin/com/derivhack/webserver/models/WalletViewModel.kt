package com.derivhack.webserver.models

import net.corda.core.identity.Party
import org.isda.cdm.Execution

class WalletViewModel(val holder: String,
                      val walletRef: String,
                      val accountNumber: String,
                      val accountName: String,
                      val currency: String,
                      val amount: Long
                      )
