package io.github.itlearner0.tvremote.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import io.github.itlearner0.tvremote.protocol.BtHidKeys
import java.util.UUID

/**
 * Télécommande Bluetooth basse consommation (HID sur GATT, « HOGP ») : le
 * protocole des télécommandes des TV Android. Le téléphone s'annonce comme
 * périphérique HID ; la TV le trouve dans « Ajouter un accessoire », s'y
 * connecte et s'abonne aux rapports de touches, envoyés en notifications.
 */
@SuppressLint("MissingPermission", "NewApi")
class BleHid(
    private val context: Context,
    private val log: (String) -> Unit,
    private val onConnection: (device: BluetoothDevice?) -> Unit,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private var server: BluetoothGattServer? = null
    private var keyboardReport: BluetoothGattCharacteristic? = null
    private var consumerReport: BluetoothGattCharacteristic? = null
    private val pending = ArrayDeque<BluetoothGattService>()
    @Volatile var device: BluetoothDevice? = null
        private set
    private val subscribed = mutableSetOf<UUID>()
    private var advertising = false

    val running: Boolean get() = server != null

    /** Démarre le serveur HID et l'annonce. */
    fun start() {
        if (server != null) {
            advertise()
            return
        }
        val gatt = manager?.openGattServer(context, callback)
        if (gatt == null) {
            log("BLE : serveur indisponible sur ce téléphone")
            return
        }
        server = gatt
        pending.clear()
        pending += hidService()
        pending += batteryService()
        pending += deviceInfoService()
        log("BLE : préparation des services de télécommande…")
        gatt.addService(pending.removeFirst())
    }

    fun stop() {
        stopAdvertising()
        runCatching { device?.let { server?.cancelConnection(it) } }
        runCatching { server?.close() }
        server = null
        device = null
        subscribed.clear()
        onConnection(null)
    }

    /** Envoie un rapport clavier (8 octets) ou multimédia (2 octets), sans identifiant de rapport. */
    fun send(consumer: Boolean, value: ByteArray): Boolean {
        val d = device ?: return false
        val gatt = server ?: return false
        val characteristic = (if (consumer) consumerReport else keyboardReport) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.notifyCharacteristicChanged(d, characteristic, false, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            gatt.notifyCharacteristicChanged(d, characteristic, false)
        }
    }

    private fun advertise() {
        val advertiser = manager?.adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            log("BLE : ce téléphone ne peut pas s'annoncer en basse consommation")
            return
        }
        if (advertising) return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(HID_SERVICE))
            .setIncludeTxPowerLevel(false)
            .build()
        val scan = AdvertiseData.Builder().setIncludeDeviceName(true).build()
        advertiser.startAdvertising(settings, data, scan, advertiseCallback)
    }

    private fun stopAdvertising() {
        if (!advertising) return
        runCatching { manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        advertising = false
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
            log("BLE : téléphone visible comme télécommande (nom : ${manager?.adapter?.name})")
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            log("BLE : annonce refusée par Android (code $errorCode)")
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) log("BLE : service ${service.uuid.short()} refusé ($status)")
            val next = pending.removeFirstOrNull()
            if (next != null) server?.addService(next) else {
                log("BLE : services prêts")
                advertise()
            }
        }

        override fun onConnectionStateChange(d: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("BLE : la TV se connecte (${d.name ?: d.address})")
                    device = d
                    stopAdvertising()
                    onConnection(d)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("BLE : déconnecté (${d.name ?: d.address}, état $status)")
                    if (device?.address == d.address) {
                        device = null
                        subscribed.clear()
                        onConnection(null)
                        // La TV se reconnecte d'elle-même à une télécommande associée qui s'annonce.
                        if (server != null) advertise()
                    }
                }
            }
        }

        override fun onCharacteristicReadRequest(d: BluetoothDevice, requestId: Int, offset: Int, c: BluetoothGattCharacteristic) {
            val value = when (c.uuid) {
                REPORT_MAP -> {
                    log("BLE : la TV lit la description des touches")
                    BtHidKeys.DESCRIPTOR
                }
                HID_INFO -> byteArrayOf(0x11, 0x01, 0x00, 0x02)
                PROTOCOL_MODE -> byteArrayOf(0x01)
                BATTERY_LEVEL -> byteArrayOf(100)
                PNP_ID -> byteArrayOf(0x02, 0x6B, 0x1D, 0x46, 0x02, 0x01, 0x00)
                REPORT -> ByteArray(if (c === consumerReport) 2 else 8)
                else -> ByteArray(0)
            }
            server?.sendResponse(d, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(minOf(offset, value.size), value.size))
        }

        override fun onDescriptorReadRequest(d: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val value = when (descriptor.uuid) {
                REPORT_REFERENCE ->
                    if (descriptor.characteristic === consumerReport) byteArrayOf(BtHidKeys.REPORT_CONSUMER.toByte(), 0x01)
                    else byteArrayOf(BtHidKeys.REPORT_KEYBOARD.toByte(), 0x01)
                CCCD -> if (subscriptionKey(descriptor.characteristic) in subscribed) byteArrayOf(0x01, 0x00) else byteArrayOf(0x00, 0x00)
                else -> ByteArray(0)
            }
            server?.sendResponse(d, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onDescriptorWriteRequest(
            d: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid == CCCD) {
                val on = value.isNotEmpty() && value[0].toInt() and 0x01 != 0
                val characteristic = descriptor.characteristic
                val key = subscriptionKey(characteristic)
                if (on) subscribed += key else subscribed -= key
                if (on && characteristic.uuid == REPORT) log("BLE : la TV écoute les touches — prête")
            }
            if (responseNeeded) server?.sendResponse(d, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onCharacteristicWriteRequest(
            d: BluetoothDevice,
            requestId: Int,
            c: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (responseNeeded) server?.sendResponse(d, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }
    }

    private fun hidService(): BluetoothGattService {
        val service = BluetoothGattService(HID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(HID_INFO, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(REPORT_MAP, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                PROTOCOL_MODE,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                HID_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
            ),
        )
        keyboardReport = inputReport().also(service::addCharacteristic)
        consumerReport = inputReport().also(service::addCharacteristic)
        return service
    }

    private fun inputReport(): BluetoothGattCharacteristic {
        val c = BluetoothGattCharacteristic(
            REPORT,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
        )
        c.addDescriptor(
            BluetoothGattDescriptor(
                CCCD,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
            ),
        )
        c.addDescriptor(BluetoothGattDescriptor(REPORT_REFERENCE, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED))
        return c
    }

    private fun batteryService() = BluetoothGattService(BATTERY_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        addCharacteristic(
            BluetoothGattCharacteristic(BATTERY_LEVEL, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ),
        )
    }

    private fun deviceInfoService() = BluetoothGattService(DEVICE_INFO_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        addCharacteristic(
            BluetoothGattCharacteristic(PNP_ID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ),
        )
    }

    private fun subscriptionKey(c: BluetoothGattCharacteristic): UUID = if (c === consumerReport) CONSUMER_KEY else c.uuid

    private fun UUID.short(): String = toString().substring(4, 8)

    companion object {
        private fun uuid16(value: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(value))
        val HID_SERVICE: UUID = uuid16(0x1812)
        val HID_INFO: UUID = uuid16(0x2A4A)
        val REPORT_MAP: UUID = uuid16(0x2A4B)
        val HID_CONTROL_POINT: UUID = uuid16(0x2A4C)
        val REPORT: UUID = uuid16(0x2A4D)
        val PROTOCOL_MODE: UUID = uuid16(0x2A4E)
        val BATTERY_SERVICE: UUID = uuid16(0x180F)
        val BATTERY_LEVEL: UUID = uuid16(0x2A19)
        val DEVICE_INFO_SERVICE: UUID = uuid16(0x180A)
        val PNP_ID: UUID = uuid16(0x2A50)
        val CCCD: UUID = uuid16(0x2902)
        val REPORT_REFERENCE: UUID = uuid16(0x2908)
        /** Clé d'abonnement distincte pour le second rapport (même UUID que le premier). */
        private val CONSUMER_KEY: UUID = UUID.fromString("00000000-0000-0000-0000-00000000c0c0")
    }
}
