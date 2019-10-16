package net.corda.cdmsupport.states

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.cdmsupport.CDMEvent
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import org.isda.cdm.Portfolio

@BelongsToContract(CDMEvent::class)
data class DBSPortfolioState(
        val portfolioJson: String,
        val workflowStatus : String,
        val executionRef: String,
        override val participants: List<Party>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    fun portfolio(): Portfolio {
        val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        return rosettaObjectMapper.readValue<Portfolio>(portfolioJson, Portfolio::class.java)
    }
}
