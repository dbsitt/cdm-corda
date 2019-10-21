package net.corda.cdmsupport.functions

class AgentHolder() {
    companion object Factory {
        val settlementAgentParty = generateParty(SETTLEMENT_AGENT_STR)
        val collateralAgentParty = generateParty(COLLATERAL_AGENT_STR)
        val client1 = generateParty(CLIENT1_STR)
        val client2 = generateParty(CLIENT2_STR)
        val client3 = generateParty(CLIENT3_STR)
        val broker1 = generateParty(BROKER1_STR)
        val broker2 = generateParty(BROKER2_STR)
    }
}