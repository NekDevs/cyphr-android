package org.cyphr.app

import org.junit.Test

class CryptoStateTest {

    @Test
    fun delegatesAreSyntacticallyValid() {
        CryptoState.activeProfileUuid
        CryptoState.profileKeysetBytes
        CryptoState.profilePublicKeyBytes
        CryptoState.listProfiles()
        CryptoState.getMyFingerprint()
        CryptoState.getMyShortFingerprint()
        CryptoState.getMyExchangeBlob()
    }
}
