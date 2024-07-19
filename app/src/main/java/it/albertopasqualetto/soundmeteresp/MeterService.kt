package it.albertopasqualetto.soundmeteresp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.util.Timer
import java.util.TimerTask


class MeterService : Service() {
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var timer: Timer

    private lateinit var recordThread: AudioRecordThread
    private lateinit var readThread: AudioReadThread

    @SuppressLint("MissingPermission")  // izin diminta di MainActivity
    override fun onCreate() {
        super.onCreate()
        meter = AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)

        // Buat Saluran Pemberitahuan, tetapi hanya pada API level 26+ karena
        // kelas NotifikasiChannel baru dan tidak ada di perpustakaan dukungan.
        // Lihat https://developer.android.com/training/notify-user/channels
        val channel = NotificationChannel(CHANNEL_ID, "SoundMeterESP", NotificationManager.IMPORTANCE_LOW)
        channel.description = "SoundMeterESP"
        // Daftarkan saluran ke sistem
        val notificationManager = getSystemService(
            NotificationManager::class.java
        )
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null // Klien tidak dapat mengikat layanan ini
    }

    @SuppressLint("WakelockTimeout")    // hanya digunakan saat MainActivity ada di layar, ditangani di MainActivity.onPause
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (isRecording){
            Log.d(TAG, "Service already running")
            wakeLock.acquire() // mendapatkan kembali wakelock tanpa batas waktu karena sekarang sudah ada di layar
            if (this::timer.isInitialized) timer.cancel()
            return START_NOT_STICKY
        }

        // Buat notifikasi
        // jika sistem adalah API level 33 atau lebih tinggi, jika pengguna tidak mengizinkan notifikasi, izinnya tidak akan diminta hingga instalasi ulang
        val notificationBuilder: Notification.Builder =
            Notification.Builder(applicationContext, CHANNEL_ID)
        notificationBuilder.setContentTitle("SoundMeterESP")
        notificationBuilder.setContentText("Recording sounds...")
        notificationBuilder.setSmallIcon(R.drawable.ic_stat_name)
        val goToMainActivityIntent = Intent(applicationContext, MainActivity::class.java).apply {
//            this.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(applicationContext, 0, goToMainActivityIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBuilder.setContentIntent(pendingIntent)
        val notification = notificationBuilder.build() // Requires API level 16
        // Menjalankan layanan ini di latar depan,
        // menyediakan notifikasi yang sedang berlangsung untuk ditampilkan kepada pengguna
        val notificationID = 2000162 // ID untuk notifikasi ini unik dalam aplikasi
        startForeground(notificationID, notification)
        Log.d(TAG, "rec: startForeground")

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundMeterESP:$TAG").apply {
                setReferenceCounted(false)
                if (intent.getBooleanExtra(MAIN_ACTIVITY_PAUSE, false))
                    acquire(10*60*1000L /*10 minutes*/) // tetap terjaga selama 10 menit jika MainActivity telah dijeda
                else
                    acquire()   // memulai layanan saat startup atau dengan tombol putar
            }
        }
        if (intent.getBooleanExtra(MAIN_ACTIVITY_PAUSE, false)) {
            timer = Timer(true)
            val timerTask: TimerTask = object : TimerTask() {
                override fun run() {
                    Log.d(TAG, "TimerTask: stopSelf")
                    stopSelf()
                    MainActivity.coldStart = true
                    timer.cancel()
                }
            }
            timer.schedule(timerTask, 600000)    // hentikan layanan setelah batas waktu 10 menit jika MainActivity telah dijeda
        }


        Log.d(TAG, "Start recording thread")
        recordThread = AudioRecordThread()
        recordThread.start()

        Log.d(TAG, "Start reading thread")
        readThread = AudioReadThread()
        readThread.start()

        return START_NOT_STICKY
    }

    override fun onDestroy()
    {
        Log.d(TAG, "onDestroy!")
        readThread.stopReading()
        recordThread.stopRecording()

        if (meter?.state == AudioRecord.STATE_INITIALIZED)
            meter?.release() ?: Log.d(TAG, "meter was not initialized")
        meter = null

        recordThread.interrupt()

        stopForeground(STOP_FOREGROUND_REMOVE)

        if (this::timer.isInitialized) timer.cancel()
        wakeLock.release()

        super.onDestroy()
    }


    private inner class AudioRecordThread : Thread("AudioRecordThread") {
        init {
            isDaemon = true
        }

        override fun run() {
            super.run()
            if (meter == null) Log.d(TAG, "rec: meter is null")
            Log.d(TAG, "Starting AudioRecordThread")
            isRecording = true
            meter?.startRecording()

            try{
                sleep(500)
            } catch (e: InterruptedException) {
                currentThread().interrupt()
            }
            // Mulai merekam putaran
            while (isRecording)
                if (meter?.recordingState == AudioRecord.RECORDSTATE_RECORDING) readLeftRightMeter(meter!!)

            // Lepaskan sumber daya AudioRecord di sini
            Log.d(TAG, "state: "+meter?.state.toString())
            Log.d(TAG, "recordingState: "+meter?.recordingState.toString())
            if (meter?.recordingState == AudioRecord.RECORDSTATE_RECORDING) meter?.stop()
        }

        fun stopRecording() {
            // Setel isRecording ke false untuk menghentikan loop perekaman
            isRecording = false
        }
    }

    private inner class AudioReadThread : Thread("AudioReadThread") {
        init {
            isDaemon = true
        }
        var isReading = true

        override fun run() {
            super.run()
            Log.d(TAG, "Starting AudioReadThread")
            try{
                sleep(1000)
            } catch (e: InterruptedException) {
                currentThread().interrupt()
            }

            var countToSec = 0  // hitung sampai 16,6 = 1 detik
            while(isReading){
                countToSec++
                try {
                    if (countToSec >= 62) { // 1000/16 ~= 62
                        countToSec = 0
                        Values.getMaxDbLastSec()
//                        Log.d(TAG, "Saved last second's data")
                    }

                    Values.getFirstFromQueueLeft()
                    Values.getFirstFromQueueRight()
//                    Log.d(TAG, "Saved real time data")
                } catch (e: ConcurrentModificationException) {
                    Log.d(TAG, "ConcurrentModificationException")   // terjadi ketika antrian kosong, tapi itu tidak menjadi masalah
                }
                sleep(16)   // ~16 ms = 60 Hz
            }
        }

        fun stopReading() {
            // Setel isReading ke false untuk menghentikan perulangan pembacaan
            isReading = false
        }
    }


    private fun readLeftRightMeter(meter: AudioRecord) {
        val buf = ShortArray(BUFFER_SIZE)
        var readN = 0

        try{
            readN += meter.read(buf, 0, BUFFER_SIZE)
            if (readN == 0) Log.d(TAG, "readN=0")
        }catch (e: Exception){
            Log.d(TAG, e.toString())
            return
        }
        val left = buf.slice(0 until readN step 2).map { Values.pcmToDb(it) }.toFloatArray()
        val right = buf.slice(1 until readN step 2).map { Values.pcmToDb(it) }.toFloatArray()
        Log.d(TAG, "readLeftRightMeter: left: ${left.size} right: ${right.size}")
        Values.updateQueues(left, right, readN)
    }


    companion object
    {
        private var meter: AudioRecord? = null

        private const val CHANNEL_ID = "soundmeteresp"
        const val MAIN_ACTIVITY_PAUSE = "MainActivityPause"

        private var isRecording = false

        private val TAG = MeterService::class.simpleName

        const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC  // `MediaRecorder.AudioSource.UNPROCESSED` tidak didukung di semua perangkat. Sayangnya `MediaRecorder.AudioSource.UNPROCESSED` menerapkan semacam pengurangan noise yang tidak diinginkan di sini.
        const val SAMPLE_RATE = 44100   // menggunakan 44100 Hz karena seharusnya didukung di semua perangkat
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)   // getMinBufferSize mengembalikan byte, bukan short -> faktor perkalian implisit *2 (yang menjamin perekaman lancar saat dimuat)
    }




}

