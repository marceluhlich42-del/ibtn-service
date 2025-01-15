#include <jni.h>
#include <string>
#include <android/log.h>
#include <cstdio>
#include <linux/input.h>

#define LOG_TAG "KV-IButtonService"

FILE *file = nullptr;

std::string valueToString(int value,bool isShift){
    std::string result;
    switch(value){
        case 30:
            result = isShift ? "A" : "a";
            break;
        case 48:
            result = isShift ? "B" : "b";
            break;
        case 46:
            result = isShift ? "C" : "c";
            break;
        case 32:
            result = isShift ? "D" : "d";
            break;
        case 18:
            result = isShift ? "E" : "e";
            break;
        case 33:
            result = isShift ? "F" : "f";
            break;
        default:
            break;
    }
    return result;
}

extern "C" {
    JNIEXPORT jint JNICALL
    Java_de_klarverwaltung_kv_1ibuttonservice_NativeLib_openDevice(JNIEnv *env, jobject obj, jstring devicePath) {
        const char *device_path = env->GetStringUTFChars(devicePath, nullptr);
        if (device_path == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to open device");
            return 0;
        }
        file = fopen(device_path, "rb");
        return (file != nullptr) ? 1 : 0;

    }

    JNIEXPORT void JNICALL
    Java_de_klarverwaltung_kv_1ibuttonservice_NativeLib_closeDevice(JNIEnv *env, jobject obj) {
        if(file != nullptr){
            fclose(file);
            file = nullptr;
        }
    }

    JNIEXPORT jobject  JNICALL
    Java_de_klarverwaltung_kv_1ibuttonservice_NativeLib_readDallas(JNIEnv *env, jobject obj) {
        if (file != nullptr) {
            struct input_event ev = {0};
            size_t bytes = fread(&ev,sizeof(ev), 1,file);

            std::string result;

            bool shift = false;
            bool end = false;
            bool isEnvelope = false;

            while (bytes == 1) {
                if (ev.code == 42) {
                    isEnvelope = ev.value != 0;
                }
                else {
                    if (end) {
                        if ((ev.code == 32 || ev.code == 46) && ev.value == 1) {
                            if (result.length() > 16) {
                                 result = result.substr(0, 16);
                            }

                            int r = ev.code;
                            do {

                                bytes = fread( &ev, sizeof(ev),1,file);
                                if (bytes != 1 ||(ev.type == 0 && ev.code == 0 && ev.value == 0)) {
                                    break;
                                }
                            }
                            while (true);
                            jclass resultClass = env->FindClass("de/klarverwaltung/kv_ibuttonservice/IbuttonResult");
                            if (resultClass == nullptr) {
                                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Result class not found!");
                                return nullptr;
                            }
                            jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;I)V");
                            if (constructor == nullptr) {
                                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Result constructor not found!");
                                return nullptr;
                            }
                            jstring javaString = env->NewStringUTF(result.c_str());

                            // Create a new Result object
                            jobject resultObject = env->NewObject(resultClass, constructor, javaString, r);

                            // Release local references if necessary
                            env->DeleteLocalRef(javaString);

                            return resultObject;
                        }
                    }
                    else if (isEnvelope && ev.code == 53) {
                        end = ev.value != 0;
                        isEnvelope = false;
                    }
                    else if (ev.code == 54) {
                        shift = ev.value != 0;
                    }
                    else if (ev.value == 1 && !isEnvelope) {
                        std::string value;
                        if (ev.code >= 2 && ev.code <= 11 && !shift) {
                            if (ev.code != 11) {
                                value = std::to_string(ev.code - 1);
                            } else {
                                value = "0";
                            }
                        }
                        else if (ev.code == 30 || ev.code == 48 || ev.code == 46 ||
                                 ev.code == 32 || ev.code == 18 || ev.code == 33) {
                            value = valueToString(ev.code, shift);
                        }
                        result += value;
                    }
                }
                bytes = fread(&ev, sizeof(ev),1,file);
            }
        }
        else {
            return nullptr;
        }
        return nullptr ;
    }
}