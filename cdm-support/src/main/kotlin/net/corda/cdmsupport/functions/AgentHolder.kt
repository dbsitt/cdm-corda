package net.corda.cdmsupport.functions

class AgentHolder() {
    companion object Factory {
        public val settlementAgentParty = generateParty(SETTLEMENT_AGENT_STR)
        public val collateralAgentParty = generateParty(COLLATERAL_AGENT_STR)
    }
}