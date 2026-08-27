#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_aitoy_MainActivity_stringFromNative(
        JNIEnv* env,
        jobject /* this */) {
    std::string message = "Native bridge is ready";
    return env->NewStringUTF(message.c_str());
}
