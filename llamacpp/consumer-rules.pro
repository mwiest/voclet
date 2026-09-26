# jni.cpp looks this up by name and Kotlin never calls it, so R8 would strip it.
-keepclassmembers class org.nehuatl.llamacpp.LLamaContext$PartialCompletionCallback {
    void onPartialCompletion(java.util.Map);
}
