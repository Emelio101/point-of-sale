package com.pos.ui.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pos.models.AppScreen
import com.pos.models.CartItem
import com.pos.models.PrintJob
import com.pos.ui.theme.PointOfSaleTheme

@Composable
fun MainAppHost(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    storeName: String,
    onStoreNameChange: (String) -> Unit,
    currencySymbol: String,
    currencyCode: String,
    onCurrencyChange: (String, String) -> Unit,
    bluetoothAdapter: BluetoothAdapter?,
    nfcUid: String, nfcTech: String, nfcExtraData: String, deviceName: String,
    connectToDevice: (BluetoothDevice) -> Unit,
    executePrint: (PrintJob, List<CartItem>, Double) -> Unit,
    onRefreshNfc: () -> Unit
) {
    val (currentScreen, setCurrentScreen) = remember { mutableStateOf(AppScreen.DIAGNOSTIC) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.DIAGNOSTIC,
                    onClick = { setCurrentScreen(AppScreen.DIAGNOSTIC) },
                    icon = { Icon(Icons.Outlined.Build, contentDescription = "Hardware") },
                    label = { Text("Hardware") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.CHECKOUT,
                    onClick = { setCurrentScreen(AppScreen.CHECKOUT) },
                    icon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = "Checkout") },
                    label = { Text("Checkout") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.SETTINGS,
                    onClick = { setCurrentScreen(AppScreen.SETTINGS) },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                AppScreen.DIAGNOSTIC -> DiagnosticScreen(
                    bluetoothAdapter, nfcUid, nfcTech, nfcExtraData, deviceName,
                    connectToDevice,
                    { job -> executePrint(job, emptyList(), 0.0) },
                    onRefreshNfc
                )

                AppScreen.CHECKOUT -> CheckoutScreen(
                    currencySymbol, storeName, deviceName
                ) { cart, total -> executePrint(PrintJob.CHECKOUT, cart, total) }

                AppScreen.SETTINGS -> SettingsScreen(
                    isDarkTheme, onThemeChange, currencyCode, currencySymbol, onCurrencyChange,
                    storeName, onStoreNameChange
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainAppHostPreview() {
    PointOfSaleTheme {
        MainAppHost(
            isDarkTheme = false,
            onThemeChange = {},
            storeName = "My Store",
            currencySymbol = "K",
            currencyCode = "ZMW",
            onCurrencyChange = { _, _ -> },
            bluetoothAdapter = null,
            nfcUid = "08D9A906",
            nfcTech = "IsoDep, NfcA",
            nfcExtraData = "EMV Bank Card Detected\n(Data Encrypted)",
            deviceName = "Emelio E101",
            connectToDevice = {},
            executePrint = { _, _, _ -> },
            onRefreshNfc = {},
            onStoreNameChange = {}
        )
    }
}