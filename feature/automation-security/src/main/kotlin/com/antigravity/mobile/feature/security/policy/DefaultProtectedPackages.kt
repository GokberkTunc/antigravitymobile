package com.antigravity.mobile.feature.security.policy

/**
 * Default protected package denylist (Confirmed Design Decision #5).
 *
 * When any package in this set is in the foreground, UI/screen automation tools
 * (`ui_action`, `ui_input_raw`, `screen_capture`) ALWAYS require explicit user approval.
 */
object DefaultProtectedPackages {

    val TURKISH_BANKS: Set<String> = linkedSetOf(
        "com.garanti.cepsubesi",                    // Garanti BBVA
        "com.pozitron.iscep",                       // İşCep (Türkiye İş Bankası)
        "com.ziraat.ziraatmobil",                   // Ziraat Mobil
        "com.akbank.android.apps.akbank_direkt",    // Akbank
        "com.ykb.android",                          // Yapı Kredi Mobil
        "com.finansbank.mobile.cepsube",            // QNB Finansbank
        "com.finansbank.mobile.enpara",             // Enpara.com Cep Şubesi
        "com.mobillium.papara",                     // Papara
        "com.denizbank.mobildeniz",                 // MobilDeniz (DenizBank)
        "com.vakifbank.mobile",                     // VakıfBank Mobil
    )

    val GOVERNMENT: Set<String> = linkedSetOf(
        "tr.gov.turkiye.edevlet.kapisi",            // e-Devlet Kapısı
    )

    val AUTHENTICATORS: Set<String> = linkedSetOf(
        "com.google.android.apps.authenticator2",   // Google Authenticator
        "com.azure.authenticator",                  // Microsoft Authenticator
        "com.authy.authy",                          // Twilio Authy
        "com.beemdevelopment.aegis",                // Aegis Authenticator
        "com.twofasapp",                            // 2FAS Auth
    )

    val PASSWORD_MANAGERS: Set<String> = linkedSetOf(
        "com.x8bit.bitwarden",                      // Bitwarden
        "com.onepassword.android",                  // 1Password
        "com.kunzisoft.keepass.free",               // KeePassDX
        "com.kunzisoft.keepass.libre",              // KeePassDX Libre
        "com.kunzisoft.keepass.pro",                // KeePassDX Pro
    )

    val DIGITAL_WALLETS: Set<String> = linkedSetOf(
        "com.google.android.apps.walletnfcrel",     // Google Wallet
    )

    val ALL: Set<String> = linkedSetOf<String>().apply {
        addAll(TURKISH_BANKS)
        addAll(GOVERNMENT)
        addAll(AUTHENTICATORS)
        addAll(PASSWORD_MANAGERS)
        addAll(DIGITAL_WALLETS)
    }
}
