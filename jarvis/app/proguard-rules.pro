# --- JARVIS keep rules ------------------------------------------------------

# llama.cpp JNI bridge: native code looks up methods by exact name/signature
-keep class com.jarvis.assistant.llm.LlamaCppEngine { *; }

# sherpa-onnx: JNI peers (long ptr handles + external methods)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# OkHttp / Okio platform warnings are noise on Android
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
