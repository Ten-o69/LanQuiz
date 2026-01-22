package io.github.ten_o69.lanquiz.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executor

class NsdHelper(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val mainExecutor: Executor = Executor { r -> Handler(Looper.getMainLooper()).post(r) }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var multicastUsers = 0
    private val infoCallbacks = LinkedHashSet<NsdManager.ServiceInfoCallback>()
    private val callbacksLock = Any()

    private val serviceType = "_lanquiz._tcp." // можно без точки, но с точкой обычно надёжнее в mDNS

    fun registerRoom(serviceName: String, port: Int, onResult: (String) -> Unit, onError: (String) -> Unit) {
        holdMulticastLock()
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = this@NsdHelper.serviceType
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                // Android может изменить имя при конфликте
                onResult(serviceInfo.serviceName)
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onError("NSD register failed: $errorCode")
                releaseMulticastLock()
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                releaseMulticastLock()
            }
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregister() {
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
        }
        registrationListener = null
        releaseMulticastLock()
    }

    /**
     * Ищем сервисы и резолвим те, у которых имя содержит нужный code (например QUIZ-AB12).
     */
    fun discoverRoomByCode(code: String): Flow<ResolvedRoom> = callbackFlow {
        stopDiscovery()
        holdMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                val name = service.serviceName ?: return
                if (!name.contains(code, ignoreCase = true)) return

                if (Build.VERSION.SDK_INT >= 34) {
                    val callback = object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                            unregisterServiceInfoCallback(this)
                        }

                        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                            val host = resolveHost(serviceInfo) ?: return
                            trySend(ResolvedRoom(host, serviceInfo.port, serviceInfo.serviceName))
                            unregisterServiceInfoCallback(this)
                        }

                        override fun onServiceLost() {
                            unregisterServiceInfoCallback(this)
                        }

                        override fun onServiceInfoCallbackUnregistered() {}
                    }
                    registerServiceInfoCallback(service, callback)
                } else {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = resolveHost(serviceInfo) ?: return
                            trySend(ResolvedRoom(host, serviceInfo.port, serviceInfo.serviceName))
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                trySend(ResolvedRoom(error = "NSD discovery start failed: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        discoveryListener = listener
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            stopDiscovery()
            clearServiceInfoCallbacks()
            releaseMulticastLock()
        }
    }

    private fun stopDiscovery() {
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        clearServiceInfoCallbacks()
    }

    private fun registerServiceInfoCallback(service: NsdServiceInfo, callback: NsdManager.ServiceInfoCallback) {
        synchronized(callbacksLock) { infoCallbacks.add(callback) }
        nsdManager.registerServiceInfoCallback(service, mainExecutor, callback)
    }

    private fun unregisterServiceInfoCallback(callback: NsdManager.ServiceInfoCallback) {
        synchronized(callbacksLock) { infoCallbacks.remove(callback) }
        runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
    }

    private fun clearServiceInfoCallbacks() {
        val callbacks = synchronized(callbacksLock) {
            val copy = infoCallbacks.toList()
            infoCallbacks.clear()
            copy
        }
        callbacks.forEach { runCatching { nsdManager.unregisterServiceInfoCallback(it) } }
    }

    private fun resolveHost(serviceInfo: NsdServiceInfo): String? {
        return if (Build.VERSION.SDK_INT >= 34) {
            serviceInfo.hostAddresses.firstOrNull()?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            serviceInfo.host?.hostAddress
        }
    }

    private fun holdMulticastLock() {
        if (multicastUsers == 0) {
            multicastLock = wifiManager.createMulticastLock("lanquiz-mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        multicastUsers++
    }

    private fun releaseMulticastLock() {
        if (multicastUsers <= 0) return
        multicastUsers--
        if (multicastUsers == 0) {
            multicastLock?.let { runCatching { if (it.isHeld) it.release() } }
            multicastLock = null
        }
    }
}

data class ResolvedRoom(
    val host: String? = null,
    val port: Int? = null,
    val serviceName: String? = null,
    val error: String? = null
)
