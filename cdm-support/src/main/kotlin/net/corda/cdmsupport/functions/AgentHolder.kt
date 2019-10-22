package net.corda.cdmsupport.functions

import net.corda.cdmsupport.eventparsing.parsePartyFromJson

class AgentHolder() {
    companion object Factory {
        val settlementAgentParty = generateParty(SETTLEMENT_AGENT_STR)
        val collateralAgentParty = generateParty(COLLATERAL_AGENT_STR)
        val client3ACT0 = parsePartyFromJson("""
            {
    "account" : {
      "accountName" : {
        "value" : "Client3_ACT#0"
      },
      "accountNumber" : {
        "value" : "Client3_ACT#0_RWKEWQTNIZALJ"
      },
      "meta" : {
        "globalKey" : "PX32qM14JekVywHFJq/AQ6n++wWQ1sdPIY5vssqlzZM="
      }
    },
    "meta" : {
      "globalKey" : "1uSEAyyjswx+JzROOrB2Leufx/vRU+LtTS/vdq7yAhU=",
      "externalKey" : "Client3_ID#0_SYGE5GTL9GOG4"
    },
    "name" : {
      "value" : "Client3"
    },
    "partyId" : [ {
      "value" : "Client3_ID#0_SYGE5GTL9GOG4"
    } ]
  }
        """.trimIndent())
        val client3ACT1 = parsePartyFromJson("""
            {
    "account" : {
      "accountName" : {
        "value" : "Client3_ACT#1"
      },
      "accountNumber" : {
        "value" : "Client3_ACT#1_PUOUACUJQJ8L6"
      },
      "meta" : {
        "globalKey" : "Mw1gc1V5Fnl7lK6aAzL03a1emwCqVRx5bJ1FNfqEV2I="
      }
    },
    "meta" : {
      "globalKey" : "SqqCOMCU00VO3Ju086/INsvZT6aFHT0P+arEmM/dNCY=",
      "externalKey" : "Client3_ID#1_JFRSX8L3C0CAR"
    },
    "name" : {
      "value" : "Client3"
    },
    "partyId" : [ {
      "value" : "Client3_ID#1_JFRSX8L3C0CAR"
    } ]
  }
        """.trimIndent())
        val client3ACT2 = parsePartyFromJson("""
            {
    "account" : {
      "accountName" : {
        "value" : "Client3_ACT#2"
      },
      "accountNumber" : {
        "value" : "Client3_ACT#2_4RFEK04DUCZNL"
      },
      "meta" : {
        "globalKey" : "pvoR1cSmnohyAQeOZtPuYqyUCxn2RTQq86z/bv1ZpAA="
      }
    },
    "meta" : {
      "globalKey" : "JRkWays7KpffHoPruiykh048KjZRRA5I2YJNeGCu/EM=",
      "externalKey" : "Client3_ID#2_SZYCDJDMNV9BV"
    },
    "name" : {
      "value" : "Client3"
    },
    "partyId" : [ {
      "value" : "Client3_ID#2_SZYCDJDMNV9BV"
    } ]
  }
        """.trimIndent())

        val broker1 = parsePartyFromJson("""
            {
    "account" : {
      "accountName" : {
        "value" : "Broker1_ACT#0"
      },
      "accountNumber" : {
        "value" : "Broker1_ACT#0_WWVA12ZJ21IW2"
      },
      "meta" : {
        "globalKey" : "xvRB/LCSyPGS736BhYfTkW1AH56H1bzHHXbqZJepZ0w="
      }
    },
    "meta" : {
      "globalKey" : "3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=",
      "externalKey" : "Broker1_ID#0_4NGIDYZJ4ZBDX"
    },
    "name" : {
      "value" : "Broker1"
    },
    "partyId" : [ {
      "value" : "Broker1_ID#0_4NGIDYZJ4ZBDX"
    } ]
  }
        """.trimIndent())

        val broker2 = parsePartyFromJson("""
            {
    "account" : {
      "accountName" : {
        "value" : "Broker2_ACT#0"
      },
      "accountNumber" : {
        "value" : "Broker2_ACT#0_TTJE0NQUTRIFB"
      },
      "meta" : {
        "globalKey" : "GyBU5vHoyL4BM7ctxcCsNp3VXMlELtkERhGao8KbExY="
      }
    },
    "meta" : {
      "globalKey" : "NKbmTRySI+MwEy+qqV0+romOrBu0GsWdqogMXGtzSVM=",
      "externalKey" : "Broker2_ID#0_AN93QUDTMRPAB"
    },
    "name" : {
      "value" : "Broker2"
    },
    "partyId" : [ {
      "value" : "Broker2_ID#0_AN93QUDTMRPAB"
    } ]
  }
        """.trimIndent())
        val client1ACT2 = parsePartyFromJson("""
            {
      "account": {
        "accountName": {
          "value": "Client1_ACT#2"
        },
        "accountNumber": {
          "value": "Client1_ACT#2_BJKXFTGW4BFPY"
        },
        "meta": {
          "globalKey": "cHT7pO5Q+p3hO3sxjl+ACvyGkYYWIBaA2PpVaRO/dvs="
        }
      },
      "meta": {
        "globalKey": "xIBwSYskqfYuo71OylXWHFH1u5lbAcAdAnM4LcNheBg=",
        "externalKey": "Client1_ID#2_CAOIBGBYU6QPR"
      },
      "name": {
        "value": "Client1"
      },
      "partyId": [
        {
          "value": "Client1_ID#2_CAOIBGBYU6QPR"
        }
      ]
    }
        """.trimIndent())

        val client1ACT1 = parsePartyFromJson("""
            {
      "account": {
        "accountName": {
          "value": "Client1_ACT#1"
        },
        "accountNumber": {
          "value": "Client1_ACT#1_BGMNIH56Y4Z5E"
        },
        "meta": {
          "globalKey": "M62Opzv6HHdCWf4DdHjQr1bNPUIVLoimmhbOETl8hns="
        }
      },
      "meta": {
        "globalKey": "3/OmcxBxF1hmK3kYJIRoLBnjrj8QPo1MO8FPDpqkBfc=",
        "externalKey": "Client1_ID#1_LBLBMTTQKNFTP"
      },
      "name": {
        "value": "Client1"
      },
      "partyId": [
        {
          "value": "Client1_ID#1_LBLBMTTQKNFTP"
        }
      ]
    }
        """.trimIndent())
        val client1ACT0 = parsePartyFromJson("""
            {
      "account": {
        "accountName": {
          "value": "Client1_ACT#0"
        },
        "accountNumber": {
          "value": "Client1_ACT#0_NQQVRTLFIT4HZ"
        },
        "meta": {
          "globalKey": "BRsIov0pBnhQgjuC37YUVLXz5yno3Xkhu7yCZF+3uxY="
        }
      },
      "meta": {
        "globalKey": "GkATYHcJ0uWdD0klwf0/RCreumcOlWEKDvfHzLfNZiA=",
        "externalKey": "Client1_ID#0_NH90YY6QYVYHM"
      },
      "name": {
        "value": "Client1"
      },
      "partyId": [
        {
          "value": "Client1_ID#0_NH90YY6QYVYHM"
        }
      ]
    }
        """.trimIndent())

        val client2ACT0 = parsePartyFromJson("""
             {
    "account" : {
      "accountName" : {
        "value" : "Client2_ACT#0"
      },
      "accountNumber" : {
        "value" : "Client2_ACT#0_CZKSADYPP2K4J"
      },
      "meta" : {
        "globalKey" : "BrMBIMKTK3BGhFqG3Y09Iz7wmpca10yFgwVcYl3eNNQ="
      }
    },
    "meta" : {
      "globalKey" : "KXEpVIuKyzAZkCm7nY/xjzgHC9X+h3O9RZb6Ejik9l4=",
      "externalKey" : "Client2_ID#0_ZRHKMHVTQ0MRO"
    },
    "name" : {
      "value" : "Client2"
    },
    "partyId" : [ {
      "value" : "Client2_ID#0_ZRHKMHVTQ0MRO"
    } ]
  }
        """.trimIndent())

        val client2ACT1 = parsePartyFromJson("""
            {
    "account" : {
      "accountName" : {
        "value" : "Client2_ACT#1"
      },
      "accountNumber" : {
        "value" : "Client2_ACT#1_0RPUB0XZEXQAC"
      },
      "meta" : {
        "globalKey" : "B4fOrMbcxmVrMW43mkm7fwQGHuOs8/1NQ4XY/g2aw+g="
      }
    },
    "meta" : {
      "globalKey" : "FyO8SCN/q+p/isum7aUiJDPCUXYakcs7f9iFwDOcNYY=",
      "externalKey" : "Client2_ID#1_PCEYOUHLMFDVH"
    },
    "name" : {
      "value" : "Client2"
    },
    "partyId" : [ {
      "value" : "Client2_ID#1_PCEYOUHLMFDVH"
    } ]
  }
        """.trimIndent())

        val client2ACT2 = parsePartyFromJson("""
            {
    "account" : {
      "accountName" : {
        "value" : "Client2_ACT#2"
      },
      "accountNumber" : {
        "value" : "Client2_ACT#2_RQKCISEUOQWZJ"
      },
      "meta" : {
        "globalKey" : "Qda8Y3+NfO4qYMAv6YwU1nR0W8aYFUpNdNr8wo2xTas="
      }
    },
    "meta" : {
      "globalKey" : "qLnjlUW673zLfTmlJ7cCnztFStz00ZP1q3yDHFQyD70=",
      "externalKey" : "Client2_ID#2_W2YVROJQYNGYP"
    },
    "name" : {
      "value" : "Client2"
    },
    "partyId" : [ {
      "value" : "Client2_ID#2_W2YVROJQYNGYP"
    } ]
  }
        """.trimIndent())

    }


}