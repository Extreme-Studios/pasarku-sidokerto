# Optimization and Obfuscation rules
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep line numbers for better stack traces in case of crashes
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve the data classes to ensure JSON parsing doesn't break
# especially if you switch to GSON or Moshi later
-keepclassmembers class com.extremestudios.pasarkusidokerto.** {
    *** get*();
    *** set*(***);
}

# Keep the specific data models used for communication
-keep class com.extremestudios.pasarkusidokerto.MerchantRegistrationPayload { *; }
-keep class com.extremestudios.pasarkusidokerto.AppsScriptResult { *; }
-keep class com.extremestudios.pasarkusidokerto.PromoData { *; }
-keep class com.extremestudios.pasarkusidokerto.MainActivity$SellerData { *; }

# Material Design and AndroidX standard rules
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-dontwarn androidx.**

# If you use BuildConfig fields (like your URL)
-keep class com.extremestudios.pasarkusidokerto.BuildConfig { *; }
