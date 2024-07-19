# SoundMeterESP

<img src="https://github.com/albertopasqualetto/SoundMeterESP/assets/39854348/61a3768c-4095-48b0-b03a-72d4ea1ec5c0" alt="Ikon Aplikasi" align="right" width="128" />

Ini adalah aplikasi pengukur suara sederhana untuk Android yang dibuat untuk mata kuliah Pemrograman Sistem Tertanam (Embedded Systems Programming) di Universitas Padua.

## Deskripsi

Aplikasi ini menggunakan mikrofon internal perangkat untuk mengukur tingkat suara dalam desibel.

Tingkat suara ditampilkan dalam bentuk grafik dan teks secara "real time", dan juga terdapat riwayat tingkat suara selama 5 menit terakhir.

## Detail Teknis

Aplikasi ini ditulis dalam Kotlin dan menggunakan pustaka [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) untuk menampilkan grafik.

Aplikasi ini menggunakan Jetpack Compose untuk menampilkan antarmuka pengguna dan mengikuti pedoman serta gaya material3 sebanyak mungkin.

`targetSdk` aplikasi ini adalah API 31 (Android 12) karena perangkat saya menjalankan Android 12 (Samsung Galaxy S10) dan `minSdk` adalah API 26.
Aplikasi ini menerapkan tema warna dinamis yang diperkenalkan di Android 12 baik pada ikon maupun warna aplikasi.

Aplikasi ini menggunakan layanan latar depan untuk menjaga mikrofon tetap aktif bahkan ketika aplikasi berada di latar belakang sehingga dapat terus mengukur tingkat suara.

Nilai dB yang ditampilkan tidak akurat karena tidak dikalibrasi dengan referensi 0 dB dan `MediaRecorder.AudioSource.MIC` melakukan beberapa elaborasi pada audio (`MediaRecorder.AudioSource.UNPROCESSED` tidak digunakan karena tidak didukung di semua perangkat); namun cukup untuk melihat perbedaan antara lingkungan yang tenang dan bising.

Beberapa pustaka dari Google digunakan untuk mendapatkan tinggi layar saat rotasi guna memilih tata letak layar yang tepat ([material3-window-size-class](https://developer.android.com/reference/kotlin/androidx/compose/material3/windowsizeclass/package-summary)) dan untuk meminta serta mengelola izin runtime dengan mudah di compose ([accompanist-permissions](https://google.github.io/accompanist/permissions/)) yang bergantung pada API androidx.

## Tangkapan Layar

Tata letak horizontal dalam mode terang:
<p float="left">
  <img src="https://github.com/albertopasqualetto/SoundMeterESP/assets/39854348/4fe2afa0-f7c6-4ff9-a9fc-928e8ebb2186" alt="Tampilan mode terang horizontal detik terakhir" width="400" />
  <img src="https://github.com/albertopasqualetto/SoundMeterESP/assets/39854348/6144a309-5b99-44c0-b67c-d9e2228ef285" alt="Tampilan mode terang horizontal lima menit terakhir" width="400" /> 
</p>

Tata letak vertikal dalam mode gelap:
<p float="left">
  <img src="https://github.com/albertopasqualetto/SoundMeterESP/assets/39854348/1847befd-18a1-458f-8bc3-53aed4cc5dcb" alt="Tampilan mode gelap vertikal detik terakhir" width="200" />
  <img src="https://github.com/albertopasqualetto/SoundMeterESP/assets/39854348/8c8c0bd1-dc61-4bcf-8d44-d835ab6fd69d" alt="Tampilan mode gelap vertikal lima menit terakhir" width="200" /> 
</p>