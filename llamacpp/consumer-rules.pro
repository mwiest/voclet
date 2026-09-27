# jni.cpp looks this up by name and Kotlin never calls it, so R8 would strip it.
# The class is LlamaContext even though its file is LLamaContext.kt.
-keepclassmembers class org.nehuatl.llamacpp.LlamaContext$PartialCompletionCallback {
    void onPartialCompletion(java.util.Map);
}
