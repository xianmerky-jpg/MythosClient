package com.mythos.client.vpn

import com.mythos.client.model.VpnSnapshot
import java.util.concurrent.CopyOnWriteArraySet

object VpnStateBus {
    @Volatile var snapshot: VpnSnapshot = VpnSnapshot()
        private set

    private val listeners = CopyOnWriteArraySet<(VpnSnapshot) -> Unit>()

    fun update(value: VpnSnapshot) {
        snapshot = value
        listeners.forEach { runCatching { it(value) } }
    }

    fun addListener(listener: (VpnSnapshot) -> Unit): () -> Unit {
        listeners += listener
        listener(snapshot)
        return { listeners -= listener }
    }
}
