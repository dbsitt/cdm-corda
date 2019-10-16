package com.derivhack.webserver.models

import net.corda.core.identity.Party
import org.isda.cdm.Execution
import org.isda.cdm.Portfolio

class PortfolioViewModel(val linearId: String,
                         val parties: List<Party>,
                         val portfolio: Portfolio)
