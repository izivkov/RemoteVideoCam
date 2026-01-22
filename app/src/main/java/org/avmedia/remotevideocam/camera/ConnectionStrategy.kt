package org.avmedia.remotevideocam.camera

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.avmedia.remotevideocam.common.ILocalConnection
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

    fun getConnection(context: Context): ILocalConnection {
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

        Timber.d("Creating new connection of type: $type")
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
                    else -> NetworkServiceConnection() // Fallback
                }
        return currentConnection!!
    }

    private fun determineBestConnectionType(context: Context): ConnectionType {
        val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return if (capabilities != null &&
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ) {
            ConnectionType.NETWORK
        } else {
            // If No WiFi network, prefer WiFi Direct as it's more widely supported than Aware
            ConnectionType.WIFI_DIRECT
        }
    }
}
