package com.pos

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.edit
import com.pos.models.CartItem
import com.pos.models.PrintJob
import com.pos.ui.screens.MainAppHost
import com.pos.ui.theme.PointOfSaleTheme
import com.pos.utils.PrintUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var nfcAdapter: NfcAdapter? = null

    private val nfcUidState = mutableStateOf("")
    private val nfcTechState = mutableStateOf("")
    private val nfcExtraDataState = mutableStateOf("")

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val logTag = "POS_APP"
    private val deviceName = getFormattedDeviceName()

    // ── Receipt helpers ────────────────────────────────────────────────────
    // ESC/POS: 48 chars wide at 57mm / standard font
    private val lineWidth = 32

    private fun now() = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()).format(Date())

    /** Right-pad `left`, right-align `right` to fill LINE_WIDTH exactly. */
    private fun itemLine(left: String, right: String): String {
        val dots = ".".repeat((lineWidth - left.length - right.length).coerceAtLeast(1))
        return "${left.take(lineWidth - right.length - 1)}$dots$right"
    }

    private fun centered(text: String): String {
        val pad = ((lineWidth - text.length) / 2).coerceAtLeast(0)
        return " ".repeat(pad) + text
    }

    private val divider get() = "-".repeat(lineWidth)

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) nfcUidState.value = "NFC Hardware Not Found"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            )
        }

        val sharedPrefs = getSharedPreferences("pos_settings", MODE_PRIVATE)

        setContent {
            val (isDarkTheme, setIsDarkTheme) = remember {
                mutableStateOf(
                    sharedPrefs.getBoolean(
                        "dark_mode",
                        false
                    )
                )
            }
            val (currencySymbol, setCurrencySymbol) = remember {
                mutableStateOf(
                    sharedPrefs.getString(
                        "currency_symbol",
                        "K"
                    ) ?: "K"
                )
            }
            val (currencyCode, setCurrencyCode) = remember {
                mutableStateOf(
                    sharedPrefs.getString(
                        "currency_code",
                        "ZMW"
                    ) ?: "ZMW"
                )
            }

            val (storeName, setStoreName) = remember {
                mutableStateOf(sharedPrefs.getString("store_name", "My Store") ?: "My Store")
            }

            PointOfSaleTheme(darkTheme = isDarkTheme) {
                MainAppHost(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = {
                        setIsDarkTheme(it)
                        sharedPrefs.edit { putBoolean("dark_mode", it) }
                    },
                    storeName = storeName,
                    onStoreNameChange = { newName ->
                        setStoreName(newName)
                        sharedPrefs.edit { putString("store_name", newName) }
                    },
                    currencySymbol = currencySymbol,
                    currencyCode = currencyCode,
                    onCurrencyChange = { code, symbol ->
                        setCurrencyCode(code)
                        setCurrencySymbol(symbol)
                        sharedPrefs.edit {
                            putString("currency_code", code)
                            putString("currency_symbol", symbol)
                        }
                    },
                    bluetoothAdapter = bluetoothAdapter,
                    nfcUid = nfcUidState.value,
                    nfcTech = nfcTechState.value,
                    nfcExtraData = nfcExtraDataState.value,
                    deviceName = deviceName,
                    connectToDevice = { connectToPrinter(it) },
                    executePrint = { job, cart, total ->
                        autoConnectAndPrint(job, cart, total, currencySymbol, storeName)
                    },
                    onRefreshNfc = { clearNfcData() }
                )
            }
        }
    }

    // ── NFC ───────────────────────────────────────────────────────────────

    private fun clearNfcData() {
        nfcUidState.value = ""
        nfcTechState.value = ""
        nfcExtraDataState.value = ""
        restartNfcReader()
    }

    private fun restartNfcReader() {
        nfcAdapter?.let {
            try {
                it.disableReaderMode(this)
                val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B
                it.enableReaderMode(this, this, flags, null)
            } catch (e: Exception) {
                Log.e(logTag, "Failed to cycle NFC reader", e)
            }
        }
    }

    private fun getFormattedDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.lowercase().startsWith(manufacturer.lowercase()))
            model.replaceFirstChar { it.uppercase() }
        else
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let {
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B
            it.enableReaderMode(this, this, flags, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(nfcTag: Tag?) {
        if (nfcTag == null) return
        val isoDep = IsoDep.get(nfcTag)

        try {
            val uidHex = nfcTag.id.joinToString("") { "%02X".format(it) }
            val techList = nfcTag.techList.joinToString(", ") { it.substringAfterLast('.') }

            // If it's an ISO-DEP, treat it as a potential EMV Bank Card
            if (isoDep != null) {
                isoDep.connect()
                isoDep.timeout = 5000 // Increase timeout for slower cards

                // 1. Select PPSE (Payment System Environment)
                val apduSelectPPSE =
                    hexStringToByteArray("00A404000E325041592E5359532E444446303100")
                isoDep.transceive(apduSelectPPSE)

                // Common AIDs: Visa and Mastercard
                val aids = mapOf(
                    "VISA" to hexStringToByteArray("00A4040007A000000003101000"),
                    "MASTERCARD" to hexStringToByteArray("00A4040007A000000004101000")
                )

                var pan = "Unknown"
                var name = "Not found (Encrypted or Omitted)"
                var cardType = "Unknown Bank Card"
                var readSuccess = false

                // 2. Try selecting the payment applications
                for ((brand, apduAID) in aids) {
                    val aidResponse = isoDep.transceive(apduAID)

                    // If the card responds with 90 00 (Success)
                    if (aidResponse.size >= 2 &&
                        aidResponse[aidResponse.size - 2] == 0x90.toByte() &&
                        aidResponse[aidResponse.size - 1] == 0x00.toByte()
                    ) {
                        cardType = brand
                        readSuccess = true

                        // 3. Read Records (Brute force SFI 1-10, Records 1-10)
                        for (sfi in 1..10) {
                            for (record in 1..10) {
                                val p2 = ((sfi shl 3) or 4).toByte()
                                val readRecord =
                                    byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), p2, 0x00)
                                val res = isoDep.transceive(readRecord)

                                if (res.size > 2 && res[res.size - 2] == 0x90.toByte()) {
                                    val hexRes = res.joinToString("") { "%02X".format(it) }

                                    // Parse PAN (Tag 5A)
                                    if (hexRes.contains("5A08")) {
                                        val index = hexRes.indexOf("5A08")
                                        pan =
                                            hexRes.substring(index + 4, index + 20).replace("F", "")
                                    }
                                    // Parse Track 2 Equivalent Data (Tag 57) - often contains PAN
                                    else if (hexRes.contains("57")) {
                                        val index = hexRes.indexOf("57")
                                        val endIdx = hexRes.indexOf("D", index + 4)
                                        if (endIdx > -1) {
                                            pan = hexRes.substring(index + 4, endIdx)
                                        }
                                    }

                                    // Parse Cardholder Name (Tag 5F20)
                                    if (hexRes.contains("5F20")) {
                                        val index = hexRes.indexOf("5F20")
                                        val length =
                                            hexRes.substring(index + 4, index + 6).toInt(16)
                                        val nameHex =
                                            hexRes.substring(index + 6, index + 6 + (length * 2))
                                        name = hexToAscii(nameHex)
                                    }
                                }
                            }
                        }
                        break // We found the application, no need to check other AIDs
                    }
                }

                isoDep.close()

                if (readSuccess) {
                    val formattedInfo =
                        "Brand: $cardType\nName: $name\nCard Number: ${formatPan(pan)}"
                    Log.d(
                        logTag,
                        "Bank Card Scanned:\nID: $uidHex\n$formattedInfo\nTECH: $techList"
                    )

                    runOnUiThread {
                        nfcUidState.value = uidHex
                        nfcTechState.value = techList
                        nfcExtraDataState.value = formattedInfo
                        Toast.makeText(this, "$cardType Read!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // It's an IsoDep tag, but neither Visa nor MC responded
                    runOnUiThread {
                        nfcUidState.value = uidHex
                        nfcTechState.value = techList
                        nfcExtraDataState.value = "Unrecognized Bank Card (Not Visa/MC)"
                        Toast.makeText(this, "Unknown EMV Card", Toast.LENGTH_SHORT).show()
                    }
                }

            } else {
                // Fallback for non-payment tags (NDEF, Mifare)
                handleStandardTag(nfcTag, uidHex, techList)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error reading NFC/EMV", e)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Read Error: Card moved too fast?",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // --- Add this helper function below your other helpers ---
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] =
                ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun handleStandardTag(nfcTag: Tag, uidHex: String, techList: String) {
        var extraInfo = "No payload detected"

        val ndef = Ndef.get(nfcTag)
        if (ndef != null) {
            try {
                ndef.connect()
                val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage
                if (msg != null && msg.records.isNotEmpty()) {
                    val payload = msg.records[0].payload
                    val text = if (payload.size > 3) String(payload, 3, payload.size - 3) else ""
                    extraInfo = "NDEF Text: $text"
                }
                ndef.close()
            } catch (e: Exception) {
                extraInfo = "NDEF read failed"
                Log.e(logTag, "NDEF Error", e)
            }
        } else if (techList.contains("MifareClassic")) {
            val mifare = MifareClassic.get(nfcTag)
            if (mifare != null) {
                try {
                    mifare.connect()
                    val sb = StringBuilder()

                    // A dictionary of the most common Mifare/NDEF keys
                    val keysToTry = listOf(
                        MifareClassic.KEY_DEFAULT,       // FF FF FF FF FF FF
                        MifareClassic.KEY_NFC_FORUM,     // D3 F7 D3 F7 D3 F7
                        byteArrayOf(
                            0xA0.toByte(),
                            0xA1.toByte(),
                            0xA2.toByte(),
                            0xA3.toByte(),
                            0xA4.toByte(),
                            0xA5.toByte()
                        ), // MAD Key
                        byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00) // All Zeros
                    )

                    val sectorCount = mifare.sectorCount.coerceAtMost(4)

                    for (sector in 0 until sectorCount) {
                        var auth = false

                        // Try every key in our dictionary
                        for (key in keysToTry) {
                            if (mifare.authenticateSectorWithKeyA(sector, key)) {
                                auth = true
                                break
                            }
                        }

                        if (auth) {
                            val blockIndex = mifare.sectorToBlock(sector)
                            val blocksInSector = mifare.getBlockCountInSector(sector)

                            for (block in 0 until blocksInSector) {
                                if (block == blocksInSector - 1) continue // Skip trailer block

                                val data = mifare.readBlock(blockIndex + block)
                                val ascii = data.map { it.toInt().toChar() }
                                    .filter { it in ' '..'~' }
                                    .joinToString("")

                                if (ascii.isNotBlank()) {
                                    sb.append(ascii)
                                }
                            }
                        }
                    }

                    if (sb.isNotEmpty()) {
                        val rawText = sb.toString()
                        val cleanText =
                            Regex("[a-zA-Z0-9]{5,}").find(rawText)?.value ?: rawText.take(50)
                        extraInfo = "Raw Read: $cleanText"
                    } else {
                        extraInfo = "Raw Read: Data encrypted (Auth Failed)"
                    }

                    mifare.close()
                } catch (e: Exception) {
                    extraInfo = "Raw Mifare read failed"
                    Log.e(logTag, "Mifare Error", e)
                }
            }
        }

        Log.d(logTag, "Standard NFC Scanned: ID: $uidHex, TECH: $techList, INFO: $extraInfo")

        runOnUiThread {
            nfcUidState.value = uidHex
            nfcTechState.value = techList
            nfcExtraDataState.value = extraInfo
            Toast.makeText(this, "NFC Tag Read!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatPan(pan: String): String {
        if (pan == "Unknown" || pan.length < 12) return pan
        // If it's a 16 digit card
        if (pan.length >= 16) {
            return pan.substring(0, 4) + " **** **** " + pan.substring(pan.length - 4)
        }
        // Generic masking if length is weird
        return "****" + pan.takeLast(4)
    }

    private fun hexToAscii(hexStr: String): String {
        val output = StringBuilder()
        try {
            for (i in 0 until hexStr.length step 2) {
                val str = hexStr.substring(i, i + 2)
                output.append(str.toInt(16).toChar())
            }
        } catch (_: Exception) {
            return "Parse Error"
        }
        return output.toString().trim()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    // ── Bluetooth ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun connectToPrinter(device: BluetoothDevice) {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()
            runOnUiThread {
                Toast.makeText(this, "Connected to Printer!", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            runOnUiThread { Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show() }
        }
    }

    // ── Printing ──────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission", "DiscouragedApi")
    private fun autoConnectAndPrint(
        job: PrintJob,
        cartItems: List<CartItem> = emptyList(),
        cartTotal: Double = 0.0,
        currencySymbol: String = "K",
        storeName: String = "My Store"
    ) {
        if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
            val paired = bluetoothAdapter?.bondedDevices
            if (paired.isNullOrEmpty()) {
                Toast.makeText(this, "No paired printers found.", Toast.LENGTH_SHORT).show()
                return
            }
            val target = paired.firstOrNull {
                val n = it.name?.lowercase() ?: ""
                n.contains("printer") || n.contains("inner") || n.contains("pos")
            } ?: paired.first()
            connectToPrinter(target)
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) return
        }

        try {
            val out = bluetoothSocket!!.outputStream

            // ESC/POS control bytes
            val disableChinese = byteArrayOf(0x1C, 0x2E)
            val init = byteArrayOf(0x1B, 0x40)
            val center = byteArrayOf(0x1B, 0x61, 0x01)
            val left = byteArrayOf(0x1B, 0x61, 0x00)
            val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
            val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
            val lf = byteArrayOf(0x0A)
            val bigOn = byteArrayOf(0x1D, 0x21, 0x11)
            val bigOff = byteArrayOf(0x1D, 0x21, 0x00)
            val cut = byteArrayOf(0x1D, 0x56, 0x42, 0x03)

            fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
            fun wl(s: String = "") {
                w("$s\n")
            }

            out.write(init)
            out.write(disableChinese)

            // ── Logo ──────────────────────────────────────────────────────
            out.write(center)
            try {
                val resId = resources.getIdentifier("logo", "drawable", packageName)
                if (resId != 0) {
                    val bmp = BitmapFactory.decodeResource(resources, resId)
                    out.write(PrintUtils.decodeBitmapToEscPos(bmp))
                    out.write(lf)
                }
            } catch (e: Exception) {
                Log.e(logTag, "Logo print failed", e)
            }

            // ── Store header ──────────────────────────────────────────────
            out.write(center)
            out.write(boldOn)
            out.write(bigOn)
            wl(storeName.uppercase())
            out.write(bigOff)
            out.write(boldOff)
            wl(deviceName)
            wl("v${BuildConfig.VERSION_NAME}")
            out.write(lf)

            // ── Receipt body (job-specific) ───────────────────────────────
            when (job) {
                PrintJob.STANDARD -> {
                    out.write(center)
                    wl("123 Main Street")
                    wl("Lusaka, Zambia")
                    out.write(lf)
                    out.write(left)
                    wl(centered(now()))
                    wl(divider)
                    wl(itemLine("1x Coffee", "${currencySymbol}35.00"))
                    wl(itemLine("2x Croissant", "${currencySymbol}100.00"))
                    wl(divider)
                    out.write(boldOn)
                    wl(itemLine("TOTAL", "${currencySymbol}135.00"))
                    out.write(boldOff)
                    out.write(lf)
                    out.write(center)
                    wl("ITEMS: 3")
                    out.write(lf)
                    out.write(boldOn)
                    wl("Thank you for your business!")
                    out.write(boldOff)
                    wl("** TEST RECEIPT **")
                }

                PrintJob.NFC -> {
                    out.write(left)
                    wl(centered(now()))
                    wl(divider)
                    wl("NFC SCAN DATA")
                    wl(divider)
                    wl("")
                    out.write(boldOn)
                    wl("CARD UID:")
                    out.write(boldOff)
                    wl(nfcUidState.value)
                    wl("")
                    out.write(boldOn)
                    wl("TECHNOLOGY:")
                    out.write(boldOff)
                    wl(nfcTechState.value)
                    wl("")
                    out.write(boldOn)
                    wl("PAYLOAD:")
                    out.write(boldOff)
                    wl(nfcExtraDataState.value)
                    wl(divider)
                    out.write(center)
                    wl(centered("Emelio POS Diagnostic"))
                }

                PrintJob.CHECKOUT -> {
                    out.write(left)
                    wl(centered(now()))
                    wl(divider)
                    cartItems.forEachIndexed { _, item ->
                        val itemTotal = item.product.price * item.quantity
                        wl(
                            itemLine(
                                "${item.quantity}x ${item.product.name}",
                                "$currencySymbol%.2f".format(itemTotal)
                            )
                        )
                    }
                    wl(divider)
                    out.write(boldOn)
                    wl(itemLine("TOTAL", "$currencySymbol%.2f".format(cartTotal)))
                    out.write(boldOff)
                    out.write(lf)
                    out.write(center)
                    wl("ITEMS: ${cartItems.sumOf { it.quantity }}")
                    out.write(lf)
                    out.write(boldOn)
                    wl("Thank you for your business!")
                    out.write(boldOff)
                    wl("Please come again ✦")
                }
            }

            // ── Footer & cut ──────────────────────────────────────────────
            out.write(lf)
            out.write(lf)
            out.write(lf)
            out.write(cut)
            out.flush()

            Toast.makeText(this, "Printing…", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Print Error: Check connection", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {
        }
    }
}