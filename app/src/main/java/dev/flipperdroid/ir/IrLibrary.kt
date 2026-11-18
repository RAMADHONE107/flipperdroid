package dev.flipperdroid.ir

/**
 * Remote bawaan (offline) dari Flipper-IRDB.
 *
 * Sumber: https://github.com/Lucaslhm/Flipper-IRDB (lisensi CC0-1.0, domain publik).
 * File sudah disaring: hanya berisi protokol yang bisa dikirim app ini
 * (NEC, NECext, Samsung32, SIRC, RAW). Remote berbasis RC5 / RC6 / Kaseikyo
 * tidak dimasukkan karena [IrEncoder] belum bisa menghasilkan frame-nya.
 */
object IrLibrary {

    data class Device(
        val category: String,
        val brand: String,
        val model: String,
        val asset: String,
        val signals: Int
    )

    val categories = listOf("TV", "AC", "PROYEKTOR", "AUDIO", "KIPAS")

    val devices = listOf(
        Device("TV", "Samsung", "TV LED / Smart (full remote)", "tv_samsung_full.ir", 54),
        Device("TV", "LG", "32LF650V", "tv_lg_32lf650v.ir", 51),
        Device("TV", "Sharp", "Aquos", "tv_sharp_aquos.ir", 59),
        Device("TV", "Sony", "Bravia", "tv_sony_bravia.ir", 54),
        Device("TV", "Panasonic", "TH-43HS550K", "tv_panasonic_th43.ir", 44),
        Device("TV", "Toshiba", "32CV100U", "tv_toshiba_32cv100u.ir", 49),
        Device("TV", "Hisense", "EN3Y39H", "tv_hisense_en3y39h.ir", 51),
        Device("TV", "TCL", "Roku TV", "tv_tcl_roku.ir", 24),
        Device("TV", "Haier", "L42C1180", "tv_haier_l42c1180.ir", 48),
        Device("TV", "Sankey", "TV umum", "tv_sankey.ir", 57),
        Device("TV", "Xiaomi", "Mi TV", "tv_xiaomi_mi.ir", 11),
        Device("AC", "Daikin", "ARC480A41", "ac_daikin_arc480a41.ir", 19),
        Device("AC", "LG", "AC umum", "ac_lg.ir", 6),
        Device("AC", "Samsung", "AR13TYHZCWKN", "ac_samsung_ar13.ir", 42),
        Device("AC", "Sharp", "CVP10MX", "ac_sharp_cvp10mx.ir", 14),
        Device("AC", "Gree", "KFR-70G-A1", "ac_gree_kfr70.ir", 8),
        Device("AC", "Midea", "MAW05R1WBL", "ac_midea_maw05.ir", 12),
        Device("AC", "Panasonic", "CWA75C4179", "ac_panasonic_cwa75.ir", 13),
        Device("AC", "Haier", "AS18GD3HRA", "ac_haier_as18.ir", 8),
        Device("AC", "Hisense", "DG11J1-99 (Celsius)", "ac_hisense_dg11.ir", 35),
        Device("AC", "TCL", "TCL AC", "ac_tcl.ir", 11),
        Device("AC", "Xiaomi", "Mi AC", "ac_xiaomi_mi.ir", 7),
        Device("PROYEKTOR", "Epson", "EB-X7", "proj_epson_ebx7.ir", 40),
        Device("PROYEKTOR", "BenQ", "TK800M", "proj_benq_tk800m.ir", 37),
        Device("PROYEKTOR", "Acer", "XL2530", "proj_acer_xl2530.ir", 18),
        Device("PROYEKTOR", "Optoma", "DH1011", "proj_optoma_dh1011.ir", 29),
        Device("PROYEKTOR", "Sony", "RM-PJ1000", "proj_sony_rmpj1000.ir", 84),
        Device("AUDIO", "Samsung", "Soundbar HW-FM45", "audio_samsung_hwfm45.ir", 32),
        Device("AUDIO", "LG", "Soundbar NB5541", "audio_lg_nb5541.ir", 23),
        Device("AUDIO", "Sony", "Soundbar HT-XT3", "audio_sony_htxt3.ir", 37),
        Device("AUDIO", "JBL", "Cinema SB120", "audio_jbl_sb120.ir", 14),
        Device("KIPAS", "Panasonic", "FV-30BG1x", "fan_panasonic_fv30.ir", 23),
        Device("KIPAS", "Deka", "MS16", "fan_deka_ms16.ir", 8),
    )

    fun byCategory(category: String): List<Device> =
        devices.filter { it.category == category }
}
