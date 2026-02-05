package org.avmedia.remotevideocam.camera

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.aware.WifiAwareManager
import android.os.Build
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.display.NetworkServiceConnection as DisplayNetworkConnection
import timber.log.Timber

object ConnectionStrategy {

    enum class ConnectionType {
        NETWORK,
        WIFI_DIRECT,
        WIFI_AWARE,
        AUTO
    }

    private var selectedType: ConnectionType = ConnectionType.AUTO
    private var currentConnection: ILocalConnection? = null
    private var currentType: ConnectionType? = null

    fun getSelectedType(): ConnectionType = selectedType

    fun setSelectedType(type: ConnectionType) {
        selectedType = type
    }

    fun getCameraConnection(context: Context): ILocalConnection {
        val type =
                if (selectedType == ConnectionType.AUTO) {
                    determineBestConnectionType(context)
                } else {
                    selectedType
                }

        if (type == currentType && currentConnection != null) {
            return currentConnection!!
        }

        currentConnection?.stop()
        currentConnection?.disconnect(context)

        Timber.d("Creating new Camera connection of type: $type")
        currentType = type
        currentConnection =
                when (type) {
                    ConnectionType.NETWORK -> NetworkServiceConnection()
                    ConnectionType.WIFI_DIRECT -> WiFiDirectServiceConnection(isVideoCapable = true)
                    ConnectionType.WIFI_AWARE ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                WiFiAwareServiceConnection(isVideoCapable = true)
                            } else {
                                WiFiDirectServiceConnection(isVideoCapable = true)
                            }
                    else -> NetworkServiceConnection()
                }
        return currentConnection!!
    }

    fun getDisplayConnection(context: Context): ILocalConnection {
        val type =
                if (selectedType == ConnectionType.AUTO) {
                    determineBestConnectionType(context)
                } else {
                    selectedType
                }

        // For NETWORK, we must return the display-specific singleton object
        if (type == ConnectionType.NETWORK) {
            return DisplayNetworkConnection
        }

        // For p2p modes, we use the same shared connection as the camera
        return getCameraConnection(context)
    }

    private fun determineBestConnectionType_OROG(context: Context): ConnectionType {
        val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        // Priority 1: Use standard WiFi if already connected to a local router
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return ConnectionType.NETWORK
        }

        // Priority 2: Use Wi-Fi Aware if hardware supports it and is enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val awareManager =
                    context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager?
            if (awareManager?.isAvailable == true) {
                return ConnectionType.WIFI_AWARE
            }
        }

        // Priority 3: Fallback to Wi-Fi Direct
        return ConnectionType.WIFI_DIRECT
    }

    private fun determineBestConnectionType(context: Context): ConnectionType {
        // return ConnectionType.NETWORK
        // return ConnectionType.WIFI_AWARE
        return ConnectionType.WIFI_DIRECT
    }
}
