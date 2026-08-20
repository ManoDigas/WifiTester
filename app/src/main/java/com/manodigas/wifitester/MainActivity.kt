package com.manodigas.wifitester

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.Inet4Address

class MainActivity : Activity() {

    companion object {
        private const val PERMISSION_REQUEST = 1001
    }

    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var statusText: TextView
    private lateinit var connectionText: TextView
    private lateinit var networksContainer: LinearLayout
    private lateinit var scanButton: Button

    private var receiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                renderScanResults()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        window.statusBarColor = Color.rgb(13, 24, 38)
        window.navigationBarColor = Color.rgb(13, 24, 38)

        buildUi()
        refreshConnectionInfo()
        ensurePermissionsAndScan()
    }

    override fun onResume() {
        super.onResume()
        registerScanReceiver()
        refreshConnectionInfo()
    }

    override fun onPause() {
        super.onPause()
        if (receiverRegistered) {
            unregisterReceiver(scanReceiver)
            receiverRegistered = false
        }
    }

    private fun registerScanReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(scanReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 17, 28))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(28))
        }

        root.addView(textView("WiFi Tester", 28f, true).apply {
            setTextColor(Color.WHITE)
        })
        root.addView(textView("Auditoria local e segura da sua rede", 14f, false).apply {
            setTextColor(Color.rgb(153, 171, 196))
            setPadding(0, dp(2), 0, dp(18))
        })

        root.addView(sectionTitle("Conexão atual"))
        connectionText = cardText("Carregando informações da conexão…")
        root.addView(connectionText)

        statusText = textView("Pronto para analisar redes próximas.", 13f, false).apply {
            setTextColor(Color.rgb(184, 198, 218))
            setPadding(dp(2), dp(14), dp(2), dp(8))
        }
        root.addView(statusText)

        scanButton = Button(this).apply {
            text = "ANALISAR REDES PRÓXIMAS"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(29, 107, 230), 14f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { ensurePermissionsAndScan() }
        }
        root.addView(scanButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply {
            bottomMargin = dp(22)
        })

        root.addView(sectionTitle("Redes encontradas"))
        networksContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(networksContainer)

        root.addView(infoCard(
            "Uso responsável",
            "O app apenas lê informações que o Android disponibiliza ao aparelho. " +
                "Ele não tenta descobrir senhas, não força conexões e não envia dados para servidores."
        ))

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun ensurePermissionsAndScan() {
        val missing = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            statusText.text = "Permita o acesso necessário para o Android liberar a varredura de Wi‑Fi."
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
            return
        }

        startWifiScan()
    }

    private fun requiredPermissions(): List<String> {
        val result = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return result
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return

        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startWifiScan()
        } else {
            statusText.text = "Sem as permissões, o Android bloqueia a leitura das redes próximas."
        }
    }

    private fun startWifiScan() {
        if (!wifiManager.isWifiEnabled) {
            statusText.text = "O Wi‑Fi está desligado. Ative-o e tente novamente."
            networksContainer.removeAllViews()
            networksContainer.addView(actionCard(
                "Wi‑Fi desligado",
                "Abra as configurações do aparelho para ativar o Wi‑Fi.",
                "ABRIR CONFIGURAÇÕES"
            ) {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            })
            return
        }

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) {
            statusText.text = "A localização do aparelho precisa estar ativada para a varredura de Wi‑Fi nesta versão do Android."
        }

        scanButton.isEnabled = false
        statusText.text = "Analisando redes…"

        val started = try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (_: SecurityException) {
            false
        }

        if (!started) {
            statusText.text = "O Android limitou uma nova varredura. Exibindo os resultados disponíveis no aparelho."
            renderScanResults()
        }

        scanButton.postDelayed({ scanButton.isEnabled = true }, 2500)
    }

    private fun renderScanResults() {
        val rawResults = try {
            wifiManager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            statusText.text = "O Android não liberou os resultados. Verifique as permissões do app."
            emptyList()
        }

        val networks = rawResults
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .mapNotNull { (_, accessPoints) -> accessPoints.maxByOrNull { it.level } }
            .sortedByDescending { it.level }

        networksContainer.removeAllViews()

        if (networks.isEmpty()) {
            networksContainer.addView(cardText(
                "Nenhuma rede visível foi retornada. Tente novamente perto de um ponto de acesso e confirme as permissões."
            ))
            statusText.text = "Nenhuma rede disponível para análise."
            return
        }

        networks.forEach { result ->
            networksContainer.addView(networkCard(result))
        }

        statusText.text = "${networks.size} rede(s) analisada(s). Toque em analisar novamente para atualizar."
        refreshConnectionInfo()
    }

    private fun refreshConnectionInfo() {
        if (!wifiManager.isWifiEnabled) {
            connectionText.text = "Wi‑Fi desligado"
            return
        }

        val info = try {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        } catch (_: SecurityException) {
            null
        }

        if (info == null || info.networkId == -1) {
            connectionText.text = "Sem conexão Wi‑Fi ativa."
            return
        }

        val ssid = info.ssid?.removeSurrounding("\"")?.takeUnless {
            it.equals("<unknown ssid>", true)
        } ?: "Rede conectada"

        val activeNetwork = connectivityManager.activeNetwork
        val linkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }
        val ipv4 = linkProperties?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address
            ?.hostAddress
            ?: "indisponível"
        val dns = linkProperties?.dnsServers
            ?.take(2)
            ?.joinToString(", ") { it.hostAddress ?: "?" }
            ?.takeIf { it.isNotBlank() }
            ?: "indisponível"

        val signal = signalLabel(info.rssi)
        val band = bandLabel(info.frequency)

        connectionText.text = buildString {
            appendLine(ssid)
            appendLine("Sinal: $signal (${info.rssi} dBm)")
            appendLine("Banda: $band • ${info.linkSpeed} Mbps")
            appendLine("IPv4: $ipv4")
            append("DNS: $dns")
        }
    }

    private fun networkCard(result: ScanResult): View {
        val security = securityInfo(result.capabilities)
        val channel = channelFromFrequency(result.frequency)
        val signal = signalLabel(result.level)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.rgb(20, 32, 49), 16f)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val name = textView(result.SSID, 17f, true).apply {
            setTextColor(Color.WHITE)
        }
        top.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val grade = textView(security.grade, 14f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(security.color)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedStrokeBackground(security.color, 12f)
        }
        top.addView(grade)
        container.addView(top)

        container.addView(textView(
            "${security.label} • $signal (${result.level} dBm)",
            13f,
            false
        ).apply {
            setTextColor(Color.rgb(191, 205, 225))
            setPadding(0, dp(8), 0, 0)
        })

        val channelText = if (channel > 0) "canal $channel" else "canal indisponível"
        container.addView(textView(
            "${bandLabel(result.frequency)} • $channelText • ${result.frequency} MHz",
            12f,
            false
        ).apply {
            setTextColor(Color.rgb(139, 158, 185))
            setPadding(0, dp(4), 0, 0)
        })

        container.addView(textView(security.advice, 12f, false).apply {
            setTextColor(Color.rgb(158, 179, 205))
            setPadding(0, dp(8), 0, 0)
        })

        return container.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun securityInfo(capabilities: String): SecurityInfo {
        val caps = capabilities.uppercase()
        return when {
            "SAE" in caps || "WPA3" in caps -> SecurityInfo(
                "WPA3", "A", Color.rgb(87, 219, 151),
                "Proteção moderna. Continue usando uma senha longa e única."
            )
            "OWE" in caps -> SecurityInfo(
                "Enhanced Open (OWE)", "B+", Color.rgb(103, 201, 240),
                "Rede sem senha com criptografia entre aparelho e ponto de acesso."
            )
            "WPA2" in caps || "RSN" in caps -> SecurityInfo(
                "WPA2", "B", Color.rgb(114, 190, 255),
                "Boa proteção quando a senha é forte e o roteador está atualizado."
            )
            "WPA" in caps -> SecurityInfo(
                "WPA", "C", Color.rgb(255, 194, 92),
                "Padrão antigo. Prefira WPA2 ou WPA3 nas configurações do roteador."
            )
            "WEP" in caps -> SecurityInfo(
                "WEP", "D", Color.rgb(255, 135, 89),
                "Proteção obsoleta. Troque o roteador para WPA2 ou WPA3."
            )
            else -> SecurityInfo(
                "Aberta", "E", Color.rgb(255, 99, 120),
                "Sem senha. Evite dados sensíveis e prefira uma rede protegida."
            )
        }
    }

    private fun signalLabel(rssi: Int): String = when {
        rssi >= -50 -> "Excelente"
        rssi >= -60 -> "Forte"
        rssi >= -70 -> "Razoável"
        rssi >= -80 -> "Fraco"
        else -> "Muito fraco"
    }

    private fun bandLabel(frequency: Int): String = when {
        frequency >= 5925 -> "6 GHz"
        frequency >= 4900 -> "5 GHz"
        frequency in 2400..2500 -> "2,4 GHz"
        else -> "Banda desconhecida"
    }

    private fun channelFromFrequency(frequency: Int): Int = when {
        frequency == 2484 -> 14
        frequency in 2412..2472 -> (frequency - 2407) / 5
        frequency in 5000..5895 -> (frequency - 5000) / 5
        frequency in 5955..7115 -> (frequency - 5950) / 5
        else -> -1
    }

    private fun sectionTitle(text: String): TextView = textView(text, 15f, true).apply {
        setTextColor(Color.rgb(218, 229, 244))
        setPadding(dp(2), 0, 0, dp(8))
    }

    private fun cardText(text: String): TextView = textView(text, 14f, false).apply {
        setTextColor(Color.rgb(210, 221, 237))
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = roundedBackground(Color.rgb(20, 32, 49), 16f)
    }

    private fun infoCard(title: String, body: String): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.rgb(17, 42, 50), 16f)
        }
        container.addView(textView(title, 14f, true).apply {
            setTextColor(Color.rgb(123, 224, 207))
        })
        container.addView(textView(body, 12f, false).apply {
            setTextColor(Color.rgb(182, 207, 209))
            setPadding(0, dp(5), 0, 0)
        })
        return container.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
        }
    }

    private fun actionCard(title: String, body: String, action: String, onClick: () -> Unit): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.rgb(20, 32, 49), 16f)
        }
        container.addView(textView(title, 15f, true).apply { setTextColor(Color.WHITE) })
        container.addView(textView(body, 12f, false).apply {
            setTextColor(Color.rgb(170, 188, 212))
            setPadding(0, dp(4), 0, dp(8))
        })
        container.addView(Button(this).apply {
            text = action
            isAllCaps = false
            setOnClickListener { onClick() }
        })
        return container
    }

    private fun textView(text: String, size: Float, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setLineSpacing(0f, 1.12f)
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun roundedStrokeBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        setStroke(dp(1), color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    data class SecurityInfo(
        val label: String,
        val grade: String,
        val color: Int,
        val advice: String
    )
}
