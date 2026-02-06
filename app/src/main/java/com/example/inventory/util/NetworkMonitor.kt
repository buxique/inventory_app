package com.example.inventory.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 网络状态监控工具
 * 
 * 提供实时的网络连接状态监控
 */
class NetworkMonitor(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * 网络状态流
     * 
     * 发射网络连接状态变化
     */
    val networkState: Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            private val networks = mutableSetOf<Network>()
            
            override fun onAvailable(network: Network) {
                networks.add(network)
                trySend(checkNetworkState())
            }
            
            override fun onLost(network: Network) {
                networks.remove(network)
                trySend(checkNetworkState())
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                trySend(checkNetworkState())
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // 发送初始状态
        trySend(checkNetworkState())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
    
    /**
     * 检查当前网络状态
     */
    fun checkNetworkState(): NetworkState {
        val network = connectivityManager.activeNetwork
        if (network == null) {
            return NetworkState.Offline
        }
        
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            return NetworkState.Offline
        }
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 
                NetworkState.Online(NetworkType.WiFi)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 
                NetworkState.Online(NetworkType.Cellular)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 
                NetworkState.Online(NetworkType.Ethernet)
            else -> NetworkState.Online(NetworkType.Unknown)
        }
    }
    
    /**
     * 是否在线
     */
    fun isOnline(): Boolean {
        return checkNetworkState() is NetworkState.Online
    }
    
    /**
     * 是否离线
     */
    fun isOffline(): Boolean {
        return checkNetworkState() is NetworkState.Offline
    }
    
    /**
     * 是否使用WiFi
     */
    fun isWiFi(): Boolean {
        val state = checkNetworkState()
        return state is NetworkState.Online && state.type == NetworkType.WiFi
    }
    
    /**
     * 是否使用移动网络
     */
    fun isCellular(): Boolean {
        val state = checkNetworkState()
        return state is NetworkState.Online && state.type == NetworkType.Cellular
    }
}

/**
 * 网络状态
 */
sealed class NetworkState {
    /**
     * 在线状态
     */
    data class Online(val type: NetworkType) : NetworkState()
    
    /**
     * 离线状态
     */
    object Offline : NetworkState()
}

/**
 * 网络类型
 */
enum class NetworkType {
    WiFi,       // WiFi网络
    Cellular,   // 移动网络
    Ethernet,   // 有线网络
    Unknown     // 未知网络
}
